package com.florapin.app.likes

import com.florapin.app.network.api.LikesApi
import com.florapin.app.network.dto.LikerDto
import com.florapin.app.network.dto.ReactionRequest
import com.florapin.app.network.dto.Reactions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private class FakeLikersApi : LikesApi {
    var currentLikers: List<LikerDto> = emptyList()
    var listCalls: Int = 0
        private set

    override suspend fun likers(flowerId: String): List<LikerDto> {
        listCalls += 1
        return currentLikers
    }

    override suspend fun like(flowerId: String): Response<Unit> = Response.success(Unit)

    override suspend fun react(
        flowerId: String,
        body: ReactionRequest,
    ): Response<Unit> = Response.success(Unit)

    override suspend fun unlike(flowerId: String): Response<Unit> = Response.success(Unit)
}

private fun liker(id: String) = LikerDto(
    userId = id,
    displayName = id,
    reaction = Reactions.HEART,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LikersViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `reouvrir la liste recharge les ajouts et retraits de reactions`() =
        runTest(dispatcher) {
            val api = FakeLikersApi()
            val vm = LikersViewModel(api)

            api.currentLikers = listOf(liker("Marie"))
            vm.bind("flower-1")
            advanceUntilIdle()
            assertEquals(listOf("Marie"), vm.state.value.likers.map { it.userId })

            api.currentLikers = listOf(liker("Marie"), liker("Antoine"))
            vm.bind("flower-1")
            advanceUntilIdle()
            assertEquals(
                listOf("Marie", "Antoine"),
                vm.state.value.likers.map { it.userId },
            )

            api.currentLikers = listOf(liker("Marie"))
            vm.bind("flower-1")
            advanceUntilIdle()
            assertEquals(listOf("Marie"), vm.state.value.likers.map { it.userId })
            assertEquals(3, api.listCalls)
        }
}
