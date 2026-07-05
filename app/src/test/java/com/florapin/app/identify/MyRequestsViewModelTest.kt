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

/** Fake dédié à l'onglet « Mes demandes » : seul listMyRequests est sollicité. */
private class FakeMyRequestsApi(
    var myRequests: List<MyIdentificationRequestDto> = emptyList(),
    var failMyRequests: Boolean = false,
) : IdentificationApi {
    override suspend fun request(flowerId: String): Response<Unit> = Response.success(null)
    override suspend fun cancel(flowerId: String): Response<Unit> = Response.success(null)
    override suspend fun listToIdentify(): List<FlowerDto> = emptyList()
    override suspend fun listMyRequests(): List<MyIdentificationRequestDto> {
        if (failMyRequests) throw RuntimeException("réseau")
        return myRequests
    }
    override suspend fun propose(
        flowerId: String,
        body: ProposeSpeciesRequest,
    ): SpeciesProposalDto = throw UnsupportedOperationException()
    override suspend fun listProposals(flowerId: String): List<SpeciesProposalDto> = emptyList()
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
    ): Response<Unit> = Response.success(null)
    override suspend fun proposalStats() =
        com.florapin.app.network.dto.ProposalStatsDto(0)
}

private fun ownedFlower(id: String) = FlowerDto(
    id = id,
    ownerId = "me",
    imageUrl = "https://x/$id.jpg",
    takenAt = "t",
    notes = "",
    visibility = "private",
    needsIdentification = true,
    createdAt = "t",
    updatedAt = "t",
)

private fun request(id: String, vararg species: String) = MyIdentificationRequestDto(
    flower = ownedFlower(id),
    proposals = species.mapIndexed { i, s ->
        SpeciesProposalDto(
            id = "p$id-$i",
            flowerId = id,
            proposedBy = "friend-$i",
            proposedByName = "Ami $i",
            species = s,
            status = "pending",
            createdAt = "t",
        )
    },
)

@OptIn(ExperimentalCoroutinesApi::class)
class MyRequestsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun load_populatesRequestsWithProposals() = runTest {
        val api = FakeMyRequestsApi(
            myRequests = listOf(request("f1", "Coquelicot"), request("f2")),
        )
        val vm = MyRequestsViewModel(api)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals(listOf("f1", "f2"), state.requests.map { it.flower.id })
        assertEquals("Coquelicot", state.requests[0].proposals.single().species)
        assertTrue(state.requests[1].proposals.isEmpty())
    }

    @Test
    fun load_failure_setsError() = runTest {
        val api = FakeMyRequestsApi(failMyRequests = true)
        val vm = MyRequestsViewModel(api)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals("réseau", state.error)
    }

    @Test
    fun refresh_setsRefreshingThenReloads() = runTest {
        val api = FakeMyRequestsApi(myRequests = listOf(request("f1")))
        val vm = MyRequestsViewModel(api)
        advanceUntilIdle()

        api.myRequests = listOf(request("f1"), request("f2"))
        vm.refresh()
        // Tirage : indicateur actif, sans écran « Chargement… » plein écran.
        assertTrue(vm.state.value.refreshing)
        assertFalse(vm.state.value.loading)

        advanceUntilIdle()
        assertFalse(vm.state.value.refreshing)
        assertEquals(listOf("f1", "f2"), vm.state.value.requests.map { it.flower.id })
    }
}
