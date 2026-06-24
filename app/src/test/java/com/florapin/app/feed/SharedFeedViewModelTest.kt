package com.florapin.app.feed

import com.florapin.app.network.api.FeedApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.LikesApi
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.FlowerDto
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
import org.junit.Assert.assertNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private fun flower(
    id: String,
    ownerId: String,
    likeCount: Int = 0,
    likedByMe: Boolean = false,
) = FlowerDto(
    id = id,
    ownerId = ownerId,
    imageUrl = "https://x/$id.jpg",
    takenAt = "2026-06-21T09:00:00Z",
    notes = "",
    visibility = "friends",
    likeCount = likeCount,
    likedByMe = likedByMe,
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

private class FakeFeedApi(private val flowers: List<FlowerDto>) : FeedApi {
    var lastSort: String? = null
    override suspend fun getFeed(since: String?, limit: Int?, sort: String?): List<FlowerDto> {
        lastSort = sort
        return flowers
    }
}

private class FakeLikesApi(private val fail: Boolean = false) : LikesApi {
    val liked = mutableListOf<String>()
    val unliked = mutableListOf<String>()
    private fun result() =
        if (fail) Response.error<Unit>(500, "x".toResponseBody()) else Response.success<Unit>(null)
    override suspend fun like(flowerId: String): Response<Unit> {
        liked += flowerId
        return result()
    }
    override suspend fun unlike(flowerId: String): Response<Unit> {
        unliked += flowerId
        return result()
    }
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
            FakeFeedApi(listOf(flower("fl1", "alice"))),
            FakeFriendshipsApi(listOf(friendship("alice", "Alice"))),
            FakeLikesApi(),
        )
        advanceUntilIdle()

        val items = vm.state.value.items
        assertEquals(1, items.size)
        assertEquals("Alice", items.first().ownerName)
    }

    @Test
    fun load_unknownOwner_nameIsNull() = runTest {
        val vm = SharedFeedViewModel(
            FakeFeedApi(listOf(flower("fl1", "inconnu"))),
            FakeFriendshipsApi(emptyList()),
            FakeLikesApi(),
        )
        advanceUntilIdle()

        assertNull(vm.state.value.items.first().ownerName)
        assertEquals(false, vm.state.value.loading)
    }

    @Test
    fun toggleLike_optimisticallyUpdatesCountAndState() = runTest {
        val likes = FakeLikesApi()
        val vm = SharedFeedViewModel(
            FakeFeedApi(listOf(flower("fl1", "alice", likeCount = 2, likedByMe = false))),
            FakeFriendshipsApi(emptyList()),
            likes,
        )
        advanceUntilIdle()

        vm.toggleLike("fl1")
        // Mise à jour optimiste immédiate (avant la confirmation réseau).
        val optimistic = vm.state.value.items.first().flower
        assertEquals(true, optimistic.likedByMe)
        assertEquals(3, optimistic.likeCount)

        advanceUntilIdle()
        assertEquals(listOf("fl1"), likes.liked)
        assertEquals(3, vm.state.value.items.first().flower.likeCount)
    }

    @Test
    fun toggleLike_revertsOnFailure() = runTest {
        val vm = SharedFeedViewModel(
            FakeFeedApi(listOf(flower("fl1", "alice", likeCount = 2, likedByMe = false))),
            FakeFriendshipsApi(emptyList()),
            FakeLikesApi(fail = true),
        )
        advanceUntilIdle()

        vm.toggleLike("fl1")
        advanceUntilIdle()

        // Échec réseau → état restauré.
        val reverted = vm.state.value.items.first().flower
        assertEquals(false, reverted.likedByMe)
        assertEquals(2, reverted.likeCount)
    }

    @Test
    fun setSort_reloadsWithSortParam() = runTest {
        val feed = FakeFeedApi(listOf(flower("fl1", "alice")))
        val vm = SharedFeedViewModel(feed, FakeFriendshipsApi(emptyList()), FakeLikesApi())
        advanceUntilIdle()
        assertEquals("date", feed.lastSort)

        vm.setSort(FeedSort.LIKES)
        advanceUntilIdle()
        assertEquals("likes", feed.lastSort)
        assertEquals(FeedSort.LIKES, vm.state.value.sort)
    }
}
