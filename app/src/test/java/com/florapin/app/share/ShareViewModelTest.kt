package com.florapin.app.share

import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.CreateShareRequest
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.network.dto.ShareDto
import com.florapin.app.network.dto.ShareToAllFriendsRequest
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

private fun friendship(id: String, status: String) = FriendshipDto(
    id = id,
    status = status,
    direction = "outgoing",
    user = FriendUserDto("u-$id", "Nom $id", "$id@b.c"),
    createdAt = "2026-06-21T09:00:00Z",
)

private fun share(id: String) = ShareDto(
    id, "owner", "u-1", "friend", "flower", "srv-1", null, true, "2026-06-21T09:00:00Z",
)

private class FakeFriendshipsApi(private val data: List<FriendshipDto>) : FriendshipsApi {
    override suspend fun list() = data
    override suspend fun request(body: CreateFriendshipRequest) = data.first()
    override suspend fun requestById(body: com.florapin.app.network.dto.AddFriendByIdRequest) =
        data.first()
    override suspend fun accept(id: String) = data.first()
    override suspend fun profile(id: String): com.florapin.app.network.dto.FriendProfileDto =
        throw UnsupportedOperationException()
    override suspend fun remove(id: String) = Response.success<Unit>(null)
}

private class FakeSharesApi(initial: List<ShareDto> = emptyList()) : SharesApi {
    var created: CreateShareRequest? = null
    var createdForAll: ShareToAllFriendsRequest? = null
    var revoked: String? = null
    var revokeFails = false
    val shares = initial.toMutableList()

    override suspend fun create(body: CreateShareRequest): ShareDto {
        created = body
        return ShareDto("s1", "owner", body.friendId, "friend", body.scope,
            body.flowerId, body.albumId, body.includeGps ?: true, "2026-06-21T09:00:00Z")
    }
    override suspend fun createForAllFriends(body: ShareToAllFriendsRequest): ShareDto {
        createdForAll = body
        return ShareDto("s1", "owner", null, "all_friends", body.scope,
            body.flowerId, body.albumId, body.includeGps ?: true, "2026-06-21T09:00:00Z")
    }
    override suspend fun listMine() = shares
    override suspend fun revoke(id: String): Response<Unit> {
        if (revokeFails) throw IOException("boom")
        revoked = id
        return Response.success(null)
    }
    override suspend fun sharedWithMe() = emptyList<com.florapin.app.network.dto.FlowerDto>()
}

private class FakeRecents(initial: List<String> = emptyList()) : RecentRecipientsStore {
    val ids = initial.toMutableList()
    override fun recentFriendIds() = ids.toList()
    override fun rememberRecentFriend(friendId: String) {
        ids.remove(friendId)
        ids.add(0, friendId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShareViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun load_keepsOnlyAcceptedFriends() = runTest {
        val vm = ShareViewModel(
            FakeFriendshipsApi(
                listOf(friendship("1", "accepted"), friendship("2", "pending")),
            ),
            FakeSharesApi(),
            FakeRecents(),
        )
        advanceUntilIdle()

        assertEquals(listOf("u-1"), vm.state.value.friends.map { it.id })
    }

    @Test
    fun load_putsRecentRecipientsFirst() = runTest {
        val vm = ShareViewModel(
            FakeFriendshipsApi(
                listOf(
                    friendship("1", "accepted"),
                    friendship("2", "accepted"),
                    friendship("3", "accepted"),
                ),
            ),
            FakeSharesApi(),
            FakeRecents(listOf("u-3", "u-2")),
        )
        advanceUntilIdle()

        assertEquals(listOf("u-3", "u-2", "u-1"), vm.state.value.friends.map { it.id })
    }

    @Test
    fun createShare_usesFlowerScopeAndRemembersRecipient() = runTest {
        val sharesApi = FakeSharesApi()
        val recents = FakeRecents()
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi, recents)
        advanceUntilIdle()

        vm.createShare("u-9", includeGps = false, flowerId = "srv-1")
        advanceUntilIdle()

        assertEquals("u-9", sharesApi.created?.friendId)
        assertEquals("flower", sharesApi.created?.scope)
        assertEquals("srv-1", sharesApi.created?.flowerId)
        assertNull(sharesApi.created?.albumId)
        assertEquals(false, sharesApi.created?.includeGps)
        assertEquals(listOf("u-9"), recents.recentFriendIds())
    }

    @Test
    fun createShare_appendsToStateWithoutReload() = runTest {
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), FakeSharesApi(), FakeRecents())
        advanceUntilIdle()

        vm.createShare("u-9", includeGps = true, flowerId = "srv-1")
        advanceUntilIdle()

        assertEquals(listOf("s1"), vm.state.value.shares.map { it.id })
    }

    @Test
    fun createShareForAll_sendsFlowerScope() = runTest {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi, FakeRecents())
        advanceUntilIdle()

        vm.createShareForAll(includeGps = false, flowerId = "srv-1")
        advanceUntilIdle()

        assertEquals("flower", sharesApi.createdForAll?.scope)
        assertEquals("srv-1", sharesApi.createdForAll?.flowerId)
        assertEquals(false, sharesApi.createdForAll?.includeGps)
    }

    @Test
    fun revoke_removesShareImmediately() = runTest {
        val sharesApi = FakeSharesApi(listOf(share("s1"), share("s2")))
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi, FakeRecents())
        advanceUntilIdle()

        vm.revoke("s1")

        // Retiré avant même que l'appel réseau ne s'achève : la liste ne se vide
        // jamais, donc la feuille ne remonte pas en haut.
        assertEquals(listOf("s2"), vm.state.value.shares.map { it.id })

        advanceUntilIdle()
        assertEquals("s1", sharesApi.revoked)
        assertEquals(listOf("s2"), vm.state.value.shares.map { it.id })
    }

    @Test
    fun revoke_restoresShareWhenApiFails() = runTest {
        val sharesApi = FakeSharesApi(listOf(share("s1"), share("s2")))
        sharesApi.revokeFails = true
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi, FakeRecents())
        advanceUntilIdle()

        vm.revoke("s1")
        advanceUntilIdle()

        assertEquals(listOf("s1", "s2"), vm.state.value.shares.map { it.id })
        assertTrue(vm.state.value.error != null)
    }
}
