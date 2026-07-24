package com.florapin.app.identify

import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.MyIdentificationRequestDto
import com.florapin.app.network.dto.ProposeSpeciesRequest
import com.florapin.app.network.dto.SpeciesProposalDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private fun flower(
    id: String,
    requestedAt: String? = null,
) = FlowerDto(
    id = id,
    ownerId = "o",
    imageUrl = "https://x/$id.jpg",
    takenAt = "t",
    notes = "",
    visibility = "shared",
    needsIdentification = true,
    identificationRequestedAt = requestedAt,
    createdAt = "t",
    updatedAt = "t",
)

private class FakeIdentificationApi(
    var flowers: List<FlowerDto> = emptyList(),
    var failList: Boolean = false,
    var failPropose: Boolean = false,
    var myRequests: List<MyIdentificationRequestDto> = emptyList(),
    var failMyRequests: Boolean = false,
) : IdentificationApi {
    val proposed = mutableListOf<Pair<String, String>>()

    override suspend fun request(flowerId: String): Response<Unit> =
        Response.success(null)

    override suspend fun remind(flowerId: String): Response<Unit> =
        Response.success(null)

    override suspend fun cancel(flowerId: String): Response<Unit> =
        Response.success(null)

    override suspend fun listToIdentify(): List<FlowerDto> {
        if (failList) throw RuntimeException("réseau")
        return flowers
    }

    override suspend fun listMyRequests(): List<MyIdentificationRequestDto> {
        if (failMyRequests) throw RuntimeException("réseau")
        return myRequests
    }

    override suspend fun propose(
        flowerId: String,
        body: ProposeSpeciesRequest,
    ): SpeciesProposalDto {
        if (failPropose) throw RuntimeException("échec")
        proposed += flowerId to body.species
        return SpeciesProposalDto(
            id = "p-$flowerId",
            flowerId = flowerId,
            proposedBy = "me",
            species = body.species,
            status = "pending",
            createdAt = "t",
        )
    }

    override suspend fun listProposals(flowerId: String): List<SpeciesProposalDto> =
        emptyList()

    override suspend fun acceptProposal(
        flowerId: String,
        proposalId: String,
    ): SpeciesProposalDto = throw UnsupportedOperationException()

    override suspend fun thankProposal(
        flowerId: String,
        proposalId: String,
    ): SpeciesProposalDto = throw UnsupportedOperationException()

    override suspend fun rejectProposal(
        flowerId: String,
        proposalId: String,
    ): retrofit2.Response<Unit> = retrofit2.Response.success(null)

    override suspend fun proposalStats() =
        com.florapin.app.network.dto.ProposalStatsDto(0)
}

@OptIn(ExperimentalCoroutinesApi::class)
class IdentifyViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun load_populatesFlowers() = runTest {
        val api = FakeIdentificationApi(flowers = listOf(flower("f1"), flower("f2")))
        val vm = IdentifyViewModel(api)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(listOf("f1", "f2"), state.flowers.map { it.id })
        assertNull(state.error)
    }

    @Test
    fun load_sortsNewestIdentificationRequestsFirst() = runTest {
        val api = FakeIdentificationApi(
            flowers = listOf(
                flower("older", requestedAt = "2026-07-23T12:00:00Z"),
                flower("newer", requestedAt = "2026-07-24T12:00:00Z"),
            ),
        )
        val vm = IdentifyViewModel(api)
        advanceUntilIdle()

        assertEquals(listOf("newer", "older"), vm.state.value.flowers.map { it.id })
    }

    @Test
    fun load_failure_setsError() = runTest {
        val api = FakeIdentificationApi(failList = true)
        val vm = IdentifyViewModel(api)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals("réseau", state.error)
    }

    @Test
    fun refresh_setsRefreshingThenReloads() = runTest {
        val api = FakeIdentificationApi(flowers = listOf(flower("f1")))
        val vm = IdentifyViewModel(api)
        advanceUntilIdle()

        // Nouvelle demande côté serveur, révélée par le tirage.
        api.flowers = listOf(flower("f1"), flower("f2"))
        vm.refresh()
        // Tirage : indicateur actif, sans écran « Chargement… » plein écran.
        assertTrue(vm.state.value.refreshing)
        assertFalse(vm.state.value.loading)

        advanceUntilIdle()
        assertFalse(vm.state.value.refreshing)
        assertEquals(listOf("f1", "f2"), vm.state.value.flowers.map { it.id })
    }

    @Test
    fun propose_marksFlowerProposed() = runTest {
        val api = FakeIdentificationApi(flowers = listOf(flower("f1")))
        val vm = IdentifyViewModel(api)
        advanceUntilIdle()

        vm.propose("f1", "  Rosa canina  ")
        advanceUntilIdle()

        assertEquals(listOf("f1" to "Rosa canina"), api.proposed)
        assertTrue("f1" in vm.state.value.proposedIds)
        assertTrue(vm.state.value.submittingIds.isEmpty())
    }

    @Test
    fun propose_blank_isIgnored() = runTest {
        val api = FakeIdentificationApi(flowers = listOf(flower("f1")))
        val vm = IdentifyViewModel(api)
        advanceUntilIdle()

        vm.propose("f1", "   ")
        advanceUntilIdle()

        assertTrue(api.proposed.isEmpty())
        assertFalse("f1" in vm.state.value.proposedIds)
    }

    @Test
    fun propose_failure_recordsError() = runTest {
        val api = FakeIdentificationApi(
            flowers = listOf(flower("f1")),
            failPropose = true,
        )
        val vm = IdentifyViewModel(api)
        advanceUntilIdle()

        vm.propose("f1", "Rosa")
        advanceUntilIdle()

        assertEquals("échec", vm.state.value.submitErrors["f1"])
        assertFalse("f1" in vm.state.value.proposedIds)
    }
}
