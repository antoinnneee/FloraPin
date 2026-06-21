package com.florapin.app.feed

import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.CreateShareRequest
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.network.dto.ShareDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private fun flower(id: String, ownerId: String) = FlowerDto(
    id = id,
    ownerId = ownerId,
    imageUrl = "https://x/$id.jpg",
    takenAt = "2026-06-21T09:00:00Z",
    notes = "",
    visibility = "friends",
    createdAt = "2026-06-21T09:00:00Z",
    updatedAt = "2026-06-21T09:00:00Z",
)

private fun friendship(userId: String, name: String) = FriendshipDto(
    id = "f-$userId",
    status = "accepted",
    direction = "incoming",
    user = FriendUserDto(userId, name, "$userId@b.c"),
    createdAt = "2026-06-21T09:00:00Z",
)

private class FakeSharesApi(private val flowers: List<FlowerDto>) : SharesApi {
    override suspend fun create(body: CreateShareRequest) = throw UnsupportedOperationException()
    override suspend fun listMine() = emptyList<ShareDto>()
    override suspend fun revoke(id: String) = Response.success<Unit>(null)
    override suspend fun sharedWithMe() = flowers
}

private class FakeFriendshipsApi(private val data: List<FriendshipDto>) : FriendshipsApi {
    override suspend fun list() = data
    override suspend fun request(body: CreateFriendshipRequest) = data.first()
    override suspend fun accept(id: String) = data.first()
    override suspend fun remove(id: String) = Response.success<Unit>(null)
}

@OptIn(ExperimentalCoroutinesApi::class)
class SharedFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun load_resolvesOwnerNames() = runTest {
        val vm = SharedFeedViewModel(
            FakeSharesApi(listOf(flower("fl1", "alice"))),
            FakeFriendshipsApi(listOf(friendship("alice", "Alice"))),
        )
        advanceUntilIdle()

        val items = vm.state.value.items
        assertEquals(1, items.size)
        assertEquals("Alice", items.first().ownerName)
    }

    @Test
    fun load_unknownOwner_nameIsNull() = runTest {
        val vm = SharedFeedViewModel(
            FakeSharesApi(listOf(flower("fl1", "inconnu"))),
            FakeFriendshipsApi(emptyList()),
        )
        advanceUntilIdle()

        assertNull(vm.state.value.items.first().ownerName)
        assertEquals(false, vm.state.value.loading)
    }
}
