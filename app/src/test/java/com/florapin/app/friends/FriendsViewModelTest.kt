package com.florapin.app.friends

import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.FriendshipDto
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

private fun friendship(
    id: String,
    status: String,
    direction: String,
) = FriendshipDto(
    id = id,
    status = status,
    direction = direction,
    user = FriendUserDto("u-$id", "Nom $id", "$id@b.c"),
    createdAt = "2026-06-21T09:00:00Z",
)

private class FakeFriendshipsApi(
    var data: MutableList<FriendshipDto> = mutableListOf(),
) : FriendshipsApi {
    var requested: String? = null
    var accepted: String? = null
    var removed: String? = null

    override suspend fun list(): List<FriendshipDto> = data
    override suspend fun request(body: CreateFriendshipRequest): FriendshipDto {
        requested = body.addresseeId
        return friendship("new", "pending", "outgoing")
    }
    override suspend fun accept(id: String): FriendshipDto {
        accepted = id
        return friendship(id, "accepted", "incoming")
    }
    override suspend fun remove(id: String): Response<Unit> {
        removed = id
        return Response.success(null)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun categorize_splitsByStatusAndDirection() {
        val state = FriendsViewModel.categorize(
            listOf(
                friendship("1", "pending", "incoming"),
                friendship("2", "pending", "outgoing"),
                friendship("3", "accepted", "incoming"),
            ),
        )
        assertEquals(listOf("1"), state.incoming.map { it.id })
        assertEquals(listOf("2"), state.outgoing.map { it.id })
        assertEquals(listOf("3"), state.friends.map { it.id })
    }

    @Test
    fun init_loadsAndCategorizes() = runTest {
        val api = FakeFriendshipsApi(
            mutableListOf(friendship("3", "accepted", "outgoing")),
        )
        val vm = FriendsViewModel(api)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.friends.size)
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun invite_sendsRequestWithUserId() = runTest {
        val api = FakeFriendshipsApi()
        val vm = FriendsViewModel(api)
        advanceUntilIdle()

        vm.invite("  u-42  ")
        advanceUntilIdle()

        assertEquals("u-42", api.requested)
    }

    @Test
    fun accept_callsApiAndRefreshes() = runTest {
        val api = FakeFriendshipsApi()
        val vm = FriendsViewModel(api)
        advanceUntilIdle()

        vm.accept("f1")
        advanceUntilIdle()

        assertEquals("f1", api.accepted)
    }
}
