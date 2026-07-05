package com.florapin.app.share

import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.dto.AlbumDto
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
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private fun friendship(id: String, status: String) = FriendshipDto(
    id = id,
    status = status,
    direction = "outgoing",
    user = FriendUserDto("u-$id", "Nom $id", "$id@b.c"),
    createdAt = "2026-06-21T09:00:00Z",
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

private class FakeSharesApi : SharesApi {
    var created: CreateShareRequest? = null
    var createdForAll: ShareToAllFriendsRequest? = null
    var revoked: String? = null
    val shares = mutableListOf<ShareDto>()

    override suspend fun create(body: CreateShareRequest): ShareDto {
        created = body
        return ShareDto("s1", "owner", body.friendId, "friend", body.scope,
            body.flowerId, body.albumId, true, "2026-06-21T09:00:00Z")
    }
    override suspend fun createForAllFriends(body: ShareToAllFriendsRequest): ShareDto {
        createdForAll = body
        return ShareDto("s1", "owner", null, "all_friends", body.scope,
            body.flowerId, body.albumId, true, "2026-06-21T09:00:00Z")
    }
    override suspend fun listMine() = shares
    override suspend fun revoke(id: String): Response<Unit> {
        revoked = id
        return Response.success(null)
    }
    override suspend fun sharedWithMe() = emptyList<com.florapin.app.network.dto.FlowerDto>()
}

private class FakeAlbumsApi(private val data: List<AlbumDto> = emptyList()) : AlbumsApi {
    override suspend fun list() = data
    override suspend fun get(id: String) = data.first { it.id == id }
    override suspend fun create(body: com.florapin.app.network.dto.CreateAlbumRequest) =
        throw UnsupportedOperationException()
    override suspend fun rename(
        id: String,
        body: com.florapin.app.network.dto.UpdateAlbumRequest,
    ) = throw UnsupportedOperationException()
    override suspend fun delete(id: String) = Response.success<Unit>(null)
    override suspend fun addFlower(
        id: String,
        body: com.florapin.app.network.dto.AddFlowerToAlbumRequest,
    ) = throw UnsupportedOperationException()
    override suspend fun removeFlower(id: String, flowerId: String) =
        throw UnsupportedOperationException()
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
            FakeAlbumsApi(),
        )
        advanceUntilIdle()

        assertEquals(listOf("u-1"), vm.state.value.friends.map { it.id })
    }

    @Test
    fun load_exposesAlbums() = runTest {
        val albums = listOf(
            AlbumDto("a1", "owner", "Printemps", "client-a1", emptyList(),
                "2026-06-21T09:00:00Z"),
        )
        val vm = ShareViewModel(
            FakeFriendshipsApi(emptyList()),
            FakeSharesApi(),
            FakeAlbumsApi(albums),
        )
        advanceUntilIdle()

        assertEquals(listOf("a1"), vm.state.value.albums.map { it.id })
    }

    @Test
    fun createShare_flowerScope_includesFlowerId() = runTest {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi, FakeAlbumsApi())
        advanceUntilIdle()

        vm.createShare("u-9", "flower", includeGps = false, flowerId = "srv-1")
        advanceUntilIdle()

        assertEquals("u-9", sharesApi.created?.friendId)
        assertEquals("srv-1", sharesApi.created?.flowerId)
        assertEquals(false, sharesApi.created?.includeGps)
    }

    @Test
    fun createShare_allScope_dropsFlowerId() = runTest {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi, FakeAlbumsApi())
        advanceUntilIdle()

        vm.createShare("u-9", "all", includeGps = true, flowerId = "srv-1")
        advanceUntilIdle()

        assertEquals("all", sharesApi.created?.scope)
        assertNull(sharesApi.created?.flowerId)
    }

    @Test
    fun createShare_albumScope_includesAlbumIdOnly() = runTest {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi, FakeAlbumsApi())
        advanceUntilIdle()

        vm.createShare("u-9", "album", includeGps = true, flowerId = "srv-1", albumId = "a1")
        advanceUntilIdle()

        assertEquals("album", sharesApi.created?.scope)
        assertEquals("a1", sharesApi.created?.albumId)
        assertNull(sharesApi.created?.flowerId)
    }

    @Test
    fun createShareForAll_allScope_sendsScopeWithoutFlowerId() = runTest {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi, FakeAlbumsApi())
        advanceUntilIdle()

        vm.createShareForAll("all", includeGps = false, flowerId = "srv-1")
        advanceUntilIdle()

        assertEquals("all", sharesApi.createdForAll?.scope)
        assertEquals(false, sharesApi.createdForAll?.includeGps)
        assertNull(sharesApi.createdForAll?.flowerId)
    }

    @Test
    fun revoke_callsApi() = runTest {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi, FakeAlbumsApi())
        advanceUntilIdle()

        vm.revoke("s1")
        advanceUntilIdle()

        assertEquals("s1", sharesApi.revoked)
    }
}
