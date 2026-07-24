package com.florapin.app.notifications

import com.florapin.app.network.api.GroupsApi
import com.florapin.app.network.api.NotificationsApi
import com.florapin.app.network.dto.CreateGroupRequest
import com.florapin.app.network.dto.GroupDto
import com.florapin.app.network.dto.InviteMemberRequest
import com.florapin.app.network.dto.MarkAllReadDto
import com.florapin.app.network.dto.NotificationDto
import com.florapin.app.network.dto.UnreadCountDto
import com.florapin.app.network.dto.UpdateGroupRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private class FakeNotificationsApi(
    private val notification: NotificationDto,
) : NotificationsApi {
    val readIds = mutableListOf<String>()

    override suspend fun list() = listOf(notification)
    override suspend fun unreadCount() = UnreadCountDto(1)
    override suspend fun markAllRead() = MarkAllReadDto(1)
    override suspend fun markRead(id: String): NotificationDto {
        readIds += id
        return notification.copy(readAt = "now")
    }
    override suspend fun delete(id: String): Response<Unit> = Response.success(Unit)
}

private class FakeGroupsApi : GroupsApi {
    val acceptedIds = mutableListOf<String>()

    private fun group(id: String, status: String = "pending") = GroupDto(
        id = id,
        ownerId = "owner",
        name = "Sous-bois",
        role = "member",
        status = status,
        createdAt = "2026-07-24T10:00:00Z",
    )

    override suspend fun list() = emptyList<GroupDto>()
    override suspend fun get(id: String) = group(id)
    override suspend fun create(body: CreateGroupRequest) = group("created", "accepted")
    override suspend fun rename(id: String, body: UpdateGroupRequest) = group(id)
    override suspend fun delete(id: String): Response<Unit> = Response.success(Unit)
    override suspend fun invite(id: String, body: InviteMemberRequest) = group(id)
    override suspend fun accept(id: String): GroupDto {
        acceptedIds += id
        return group(id, "accepted")
    }
    override suspend fun removeMember(id: String, userId: String): Response<Unit> =
        Response.success(Unit)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NotificationCenterViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun acceptAlbumInvitation_accepts_marksRead_andRequestsSync() = runTest(dispatcher) {
        val invitation = NotificationDto(
            id = "notification-1",
            type = "group_invited",
            data = mapOf("groupId" to "group-42", "groupName" to "Sous-bois"),
            createdAt = "2026-07-24T10:00:00Z",
        )
        val notifications = FakeNotificationsApi(invitation)
        val groups = FakeGroupsApi()
        var syncRequested = false
        val viewModel = NotificationCenterViewModel(
            api = notifications,
            groupsApi = groups,
            onAlbumAccepted = { syncRequested = true },
        )
        advanceUntilIdle()

        viewModel.acceptAlbumInvitation(invitation)
        advanceUntilIdle()

        assertEquals(listOf("group-42"), groups.acceptedIds)
        assertEquals(listOf("notification-1"), notifications.readIds)
        assertTrue(syncRequested)
        assertTrue("notification-1" in viewModel.state.value.acceptedInvitationIds)
        assertFalse("notification-1" in viewModel.state.value.acceptingInvitationIds)
        assertTrue(viewModel.state.value.items.single().readAt != null)
    }
}
