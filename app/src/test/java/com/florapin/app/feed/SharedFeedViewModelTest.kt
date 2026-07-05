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
    createdAt: String = "2026-06-21T09:00:00Z",
) = FlowerDto(
    id = id,
    ownerId = ownerId,
    imageUrl = "https://x/$id.jpg",
    takenAt = "2026-06-21T09:00:00Z",
    notes = "",
    visibility = "friends",
    likeCount = likeCount,
    likedByMe = likedByMe,
    createdAt = createdAt,
    updatedAt = createdAt,
)

private fun item(id: String, createdAt: String) =
    SharedFlowerItem(flower(id, "alice", createdAt = createdAt), ownerName = "Alice")

private fun friendship(userId: String, name: String) = FriendshipDto(
    id = "f-$userId",
    status = "accepted",
    direction = "incoming",
    user = FriendUserDto(userId, name, "$userId@b.c"),
    createdAt = "2026-06-21T09:00:00Z",
)

private class FakeFeedApi(private val flowers: List<FlowerDto>) : FeedApi {
    var lastSort: String? = null
    var lastBefore: String? = null
    val calls = mutableListOf<String?>()
    override suspend fun getFeed(
        since: String?,
        limit: Int?,
        sort: String?,
        before: String?,
    ): List<FlowerDto> {
        lastSort = sort
        lastBefore = before
        calls += before
        return flowers
    }
}

/**
 * Feed paginé : sert des pages de `page` fleurs tant qu'il en reste, en filtrant
 * par le curseur `before` (format `<createdAt>_<id>`) sur l'id, comme le keyset
 * serveur (fleurs de même createdAt ici, l'id départage).
 */
private class PagingFeedApi(
    private val all: List<FlowerDto>,
    private val page: Int,
) : FeedApi {
    var callCount = 0
    override suspend fun getFeed(
        since: String?,
        limit: Int?,
        sort: String?,
        before: String?,
    ): List<FlowerDto> {
        callCount++
        val startId = before?.substringAfterLast('_')
        val remaining =
            if (startId == null) all
            else all.dropWhile { it.id != startId }.drop(1)
        return remaining.take(page)
    }
}

private class FakeLikesApi(private val fail: Boolean = false) : LikesApi {
    val liked = mutableListOf<String>()
    /** Réactions typées posées, en couples (flowerId, code de réaction). */
    val reacted = mutableListOf<Pair<String, String>>()
    val unliked = mutableListOf<String>()
    private fun result() =
        if (fail) Response.error<Unit>(500, "x".toResponseBody()) else Response.success<Unit>(null)
    override suspend fun like(flowerId: String): Response<Unit> {
        liked += flowerId
        return result()
    }
    override suspend fun react(
        flowerId: String,
        body: com.florapin.app.network.dto.ReactionRequest,
    ): Response<Unit> {
        reacted += flowerId to body.reaction
        return result()
    }
    override suspend fun unlike(flowerId: String): Response<Unit> {
        unliked += flowerId
        return result()
    }
    override suspend fun likers(flowerId: String) =
        emptyList<com.florapin.app.network.dto.LikerDto>()
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
        // Le tap pose la réaction par défaut (cœur) via l'endpoint typé.
        assertEquals(listOf("fl1" to "heart"), likes.reacted)
        assertEquals(3, vm.state.value.items.first().flower.likeCount)
    }

    @Test
    fun react_setsTypedReactionOptimistically() = runTest {
        val likes = FakeLikesApi()
        val vm = SharedFeedViewModel(
            FakeFeedApi(listOf(flower("fl1", "alice", likeCount = 2, likedByMe = false))),
            FakeFriendshipsApi(emptyList()),
            likes,
        )
        advanceUntilIdle()

        vm.react("fl1", "rose")
        val optimistic = vm.state.value.items.first().flower
        assertEquals("rose", optimistic.myReaction)
        assertEquals(true, optimistic.likedByMe)
        assertEquals(3, optimistic.likeCount)
        assertEquals(1, optimistic.reactionCounts["rose"])

        advanceUntilIdle()
        assertEquals(listOf("fl1" to "rose"), likes.reacted)
    }

    @Test
    fun react_changingTypeKeepsTotal() = runTest {
        val likes = FakeLikesApi()
        val vm = SharedFeedViewModel(
            FakeFeedApi(
                listOf(
                    flower("fl1", "alice", likeCount = 1, likedByMe = true)
                        .copy(myReaction = "heart", reactionCounts = mapOf("heart" to 1)),
                ),
            ),
            FakeFriendshipsApi(emptyList()),
            likes,
        )
        advanceUntilIdle()

        vm.react("fl1", "rose")
        val f = vm.state.value.items.first().flower
        // Changer d'emoji ne modifie pas le total (update, pas insert).
        assertEquals(1, f.likeCount)
        assertEquals("rose", f.myReaction)
        assertNull(f.reactionCounts["heart"])
        assertEquals(1, f.reactionCounts["rose"])
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
    fun loadMore_appendsNextPageAndDetectsEnd() = runTest {
        // 25 fleurs, page = 20 → page 1 (20) puis page 2 (5) puis fin.
        val all = (1..25).map { flower("fl%02d".format(it), "alice") }
        val feed = PagingFeedApi(all, page = 20)
        val vm = SharedFeedViewModel(feed, FakeFriendshipsApi(emptyList()), FakeLikesApi())
        advanceUntilIdle()

        assertEquals(20, vm.state.value.items.size)
        assertEquals(false, vm.state.value.endReached)

        vm.loadMore()
        advanceUntilIdle()

        assertEquals(25, vm.state.value.items.size)
        assertEquals(true, vm.state.value.endReached)
        // Pas de doublon dans la liste accumulée.
        assertEquals(25, vm.state.value.items.map { it.flower.id }.toSet().size)

        // Fin atteinte → loadMore() ne relance rien.
        val callsBefore = feed.callCount
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(callsBefore, feed.callCount)
    }

    @Test
    fun loadMore_noopForLikesSort() = runTest {
        val feed = FakeFeedApi(listOf(flower("fl1", "alice")))
        val vm = SharedFeedViewModel(feed, FakeFriendshipsApi(emptyList()), FakeLikesApi())
        advanceUntilIdle()

        vm.setSort(FeedSort.LIKES)
        advanceUntilIdle()
        feed.lastBefore = "sentinel"

        // Tri par cœurs : pas de pagination (fin d'emblée), before jamais envoyé.
        vm.loadMore()
        advanceUntilIdle()
        assertEquals("sentinel", feed.lastBefore)
        assertEquals(true, vm.state.value.endReached)
    }

    @Test
    fun refresh_setsRefreshingThenReloadsFirstPage() = runTest {
        val feed = FakeFeedApi(listOf(flower("fl1", "alice")))
        val vm = SharedFeedViewModel(feed, FakeFriendshipsApi(emptyList()), FakeLikesApi())
        advanceUntilIdle()

        vm.refresh()
        // Indicateur de tirage actif tant que la passe n'est pas terminée, sans
        // repasser par l'écran de chargement plein écran.
        assertEquals(true, vm.state.value.refreshing)
        assertEquals(false, vm.state.value.loading)

        advanceUntilIdle()
        assertEquals(false, vm.state.value.refreshing)
        // Recharge depuis la première page (curseur `before` non renseigné).
        assertNull(feed.lastBefore)
        assertEquals(1, vm.state.value.items.size)
    }

    @Test
    fun separator_nullWithoutCutoff() {
        // Première visite (aucun repère) → pas de séparateur.
        val items = listOf(item("a", "2026-06-21T09:00:00Z"))
        assertNull(feedNewSeparatorIndex(items, cutoff = null))
    }

    @Test
    fun separator_indexOfFirstAlreadySeenFlower() {
        // 2 nouveautés (postérieures à la visite) puis 2 déjà vues → séparateur en 2.
        val items = listOf(
            item("n1", "2026-06-21T12:00:00Z"),
            item("n2", "2026-06-21T11:00:00Z"),
            item("v1", "2026-06-21T09:00:00Z"),
            item("v2", "2026-06-21T08:00:00Z"),
        )
        assertEquals(2, feedNewSeparatorIndex(items, cutoff = "2026-06-21T10:00:00Z"))
    }

    @Test
    fun separator_nullWhenNoNewFlower() {
        // Toutes les fleurs sont antérieures à la visite → aucune nouveauté.
        val items = listOf(
            item("v1", "2026-06-21T09:00:00Z"),
            item("v2", "2026-06-21T08:00:00Z"),
        )
        assertNull(feedNewSeparatorIndex(items, cutoff = "2026-06-21T10:00:00Z"))
    }

    @Test
    fun separator_nullWhenAllFlowersAreNew() {
        // Toutes plus récentes que la visite → rien à séparer (pas de bloc « déjà vu »).
        val items = listOf(
            item("n1", "2026-06-21T12:00:00Z"),
            item("n2", "2026-06-21T11:00:00Z"),
        )
        assertNull(feedNewSeparatorIndex(items, cutoff = "2026-06-21T10:00:00Z"))
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
