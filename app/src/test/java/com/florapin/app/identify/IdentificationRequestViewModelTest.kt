package com.florapin.app.identify

import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.ProposeSpeciesRequest
import com.florapin.app.network.dto.SpeciesProposalDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private class StubApi(
    private val result: () -> Response<Unit>,
) : IdentificationApi {
    val requested = mutableListOf<String>()
    override suspend fun request(flowerId: String): Response<Unit> {
        requested += flowerId
        return result()
    }
    override suspend fun cancel(flowerId: String): Response<Unit> = Response.success(null)
    override suspend fun listToIdentify(): List<FlowerDto> = emptyList()
    override suspend fun propose(
        flowerId: String,
        body: ProposeSpeciesRequest,
    ): SpeciesProposalDto = throw UnsupportedOperationException()
}

@OptIn(ExperimentalCoroutinesApi::class)
class IdentificationRequestViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun request_success_setsSent() = runTest {
        val api = StubApi { Response.success(null) }
        val vm = IdentificationRequestViewModel(api)

        vm.request("flower-1")
        advanceUntilIdle()

        assertEquals(listOf("flower-1"), api.requested)
        assertEquals(IdentificationRequestState.Sent, vm.state.value)
    }

    @Test
    fun request_httpError_setsError() = runTest {
        val errorBody = "boom".toResponseBody("text/plain".toMediaType())
        val api = StubApi { Response.error(500, errorBody) }
        val vm = IdentificationRequestViewModel(api)

        vm.request("flower-1")
        advanceUntilIdle()

        assertTrue(vm.state.value is IdentificationRequestState.Error)
    }

    @Test
    fun request_exception_setsError() = runTest {
        val api = object : IdentificationApi {
            override suspend fun request(flowerId: String): Response<Unit> =
                throw RuntimeException("hors-ligne")
            override suspend fun cancel(flowerId: String): Response<Unit> =
                Response.success(null)
            override suspend fun listToIdentify(): List<FlowerDto> = emptyList()
            override suspend fun propose(
                flowerId: String,
                body: ProposeSpeciesRequest,
            ): SpeciesProposalDto = throw UnsupportedOperationException()
        }
        val vm = IdentificationRequestViewModel(api)

        vm.request("flower-1")
        advanceUntilIdle()

        assertEquals(
            IdentificationRequestState.Error("hors-ligne"),
            vm.state.value,
        )
    }
}
