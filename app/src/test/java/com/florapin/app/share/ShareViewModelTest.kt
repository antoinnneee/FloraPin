package com.florapin.app.share

import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.CreateShareRequest
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
    override suspend fun accept(id: String) = data.first()
    override suspend fun remove(id: String) = Response.success<Unit>(null)
}

private class FakeSharesApi : SharesApi {
    var created: CreateShareRequest? = null
    var revoked: String? = null
    val shares = mutableListOf<ShareDto>()

    override suspend fun create(body: CreateShareRequest): ShareDto {
        created = body
        return ShareDto("s1", "owner", body.friendId, body.scope, body.flowerId, true,
            "2026-06-21T09:00:00Z")
    }
    override suspend fun listMine() = shares
    override suspend fun revoke(id: String): Response<Unit> {
        revoked = id
        return Response.success(null)
    }
    override suspend fun sharedWithMe() = emptyList<com.florapin.app.network.dto.FlowerDto>()
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
        )
        advanceUntilIdle()

        assertEquals(listOf("u-1"), vm.state.value.friends.map { it.id })
    }

    @Test
    fun createShare_flowerScope_includesFlowerId() = runTest {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi)
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
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi)
        advanceUntilIdle()

        vm.createShare("u-9", "all", includeGps = true, flowerId = "srv-1")
        advanceUntilIdle()

        assertEquals("all", sharesApi.created?.scope)
        assertNull(sharesApi.created?.flowerId)
    }

    @Test
    fun revoke_callsApi() = runTest {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(FakeFriendshipsApi(emptyList()), sharesApi)
        advanceUntilIdle()

        vm.revoke("s1")
        advanceUntilIdle()

        assertEquals("s1", sharesApi.revoked)
    }
}
