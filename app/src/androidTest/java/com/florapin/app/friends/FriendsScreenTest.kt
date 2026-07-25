package com.florapin.app.friends

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.dto.AddFriendByIdRequest
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.FriendProfileDto
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.FriendshipDto
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class FriendsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun removingFriend_requiresConfirmation() {
        val friendship = FriendshipDto(
            id = "friendship-1",
            status = "accepted",
            direction = "outgoing",
            user = FriendUserDto("user-1", "Alice", "alice@example.com"),
            createdAt = "2026-07-25T09:00:00Z",
        )
        val api = FakeFriendshipsApi(mutableListOf(friendship))
        val viewModel = FriendsViewModel(api)

        compose.setContent {
            FriendsScreen(onBack = {}, viewModel = viewModel)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Retirer").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Retirer").performClick()

        compose.onAllNodesWithText("Supprimer Alice ?").assertCountEquals(1)
        assertEquals(null, api.removed)

        compose.onNodeWithText("Annuler").performClick()

        compose.onAllNodesWithText("Supprimer Alice ?").assertCountEquals(0)
        assertEquals(null, api.removed)

        compose.onNodeWithText("Retirer").performClick()
        compose.onNodeWithText("Supprimer").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { api.removed != null }
        assertEquals("friendship-1", api.removed)
    }
}

private class FakeFriendshipsApi(
    private val friendships: MutableList<FriendshipDto>,
) : FriendshipsApi {
    var removed: String? = null

    override suspend fun list(): List<FriendshipDto> = friendships.toList()

    override suspend fun request(body: CreateFriendshipRequest): FriendshipDto =
        throw UnsupportedOperationException()

    override suspend fun requestById(body: AddFriendByIdRequest): FriendshipDto =
        throw UnsupportedOperationException()

    override suspend fun accept(id: String): FriendshipDto =
        throw UnsupportedOperationException()

    override suspend fun profile(id: String): FriendProfileDto =
        throw UnsupportedOperationException()

    override suspend fun remove(id: String): Response<Unit> {
        removed = id
        friendships.removeAll { it.id == id }
        return Response.success(null)
    }
}
