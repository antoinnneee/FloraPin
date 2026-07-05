package com.florapin.app.share

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.dto.AddFlowerToAlbumRequest
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.CreateAlbumRequest
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.CreateShareRequest
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.network.dto.ShareDto
import com.florapin.app.network.dto.ShareToAllFriendsRequest
import com.florapin.app.network.dto.UpdateAlbumRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

/**
 * Tests UI Compose de la feuille de partage (NODE-71). On injecte un
 * [ShareViewModel] alimenté par des APIs factices (aucun réseau réel), sur le
 * modèle de `ShareViewModelTest`, et on pilote les interactions clés :
 * sélection d'un destinataire, périmètre, création ciblée / réseau, révocation.
 */
@RunWith(AndroidJUnit4::class)
class ShareFlowerSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private fun acceptedFriend(id: String, name: String) = FriendshipDto(
        id = "f-$id",
        status = "accepted",
        direction = "outgoing",
        user = FriendUserDto(id, name, "$id@b.c"),
        createdAt = "2026-06-21T09:00:00Z",
    )

    // Le second nœud portant le texte « Partager » est le bouton (le premier est
    // le titre de la feuille). Cet ordre suit la déclaration du composable.
    private fun submitButton() = compose.onAllNodesWithText("Partager")[1]

    @Test
    fun selectingFriend_thenSubmit_createsTargetedShare() {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(
            FakeFriendshipsApi(listOf(acceptedFriend("u1", "Alice"))),
            sharesApi,
            FakeAlbumsApi(),
        )
        compose.setContent {
            ShareFlowerSheet(flowerServerId = "srv-1", onDismiss = {}, viewModel = vm)
        }

        // Attend le chargement des amis (coroutine du ViewModel).
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Alice").fetchSemanticsNodes().isNotEmpty()
        }

        // Sans destinataire, l'envoi est bloqué.
        submitButton().assertIsNotEnabled()

        compose.onNodeWithText("Alice").performClick()
        submitButton().assertIsEnabled()
        submitButton().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { sharesApi.created != null }
        val req = sharesApi.created!!
        assertEquals("u1", req.friendId)
        // Périmètre par défaut « cette fleur » puisqu'un id serveur est fourni.
        assertEquals("flower", req.scope)
        assertEquals("srv-1", req.flowerId)
        assertEquals(true, req.includeGps)
    }

    @Test
    fun selectingAllFriends_submits_networkShare() {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(
            FakeFriendshipsApi(listOf(acceptedFriend("u1", "Alice"))),
            sharesApi,
            FakeAlbumsApi(),
        )
        compose.setContent {
            ShareFlowerSheet(flowerServerId = "srv-1", onDismiss = {}, viewModel = vm)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("👥 Tous mes amis").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("👥 Tous mes amis").performClick()

        // Le libellé du bouton bascule sur le partage réseau.
        compose.onNodeWithText("Partager avec tous mes amis").assertIsEnabled()
        compose.onNodeWithText("Partager avec tous mes amis").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { sharesApi.createdForAll != null }
        assertEquals("flower", sharesApi.createdForAll!!.scope)
        // Aucun partage ciblé n'a été émis.
        assertEquals(null, sharesApi.created)
    }

    @Test
    fun existingShare_revoke_callsApi() {
        val sharesApi = FakeSharesApi().apply {
            shares.add(
                ShareDto(
                    id = "s-seed",
                    ownerId = "owner",
                    sharedWith = "u1",
                    audience = "friend",
                    scope = "all",
                    includeGps = true,
                    createdAt = "2026-06-21T09:00:00Z",
                ),
            )
        }
        val vm = ShareViewModel(
            FakeFriendshipsApi(listOf(acceptedFriend("u1", "Alice"))),
            sharesApi,
            FakeAlbumsApi(),
        )
        compose.setContent {
            ShareFlowerSheet(flowerServerId = null, onDismiss = {}, viewModel = vm)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Révoquer").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Révoquer").performClick()

        compose.waitUntil(timeoutMillis = 5_000) { sharesApi.revoked != null }
        assertEquals("s-seed", sharesApi.revoked)
    }
}

// --- APIs factices (mêmes contrats que ShareViewModelTest, dupliquées ici car
// androidTest et src/test ne partagent pas leur code). ---

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
    override suspend fun listMine() = shares.toList()
    override suspend fun revoke(id: String): Response<Unit> {
        revoked = id
        return Response.success(null)
    }
    override suspend fun sharedWithMe() = emptyList<FlowerDto>()
}

private class FakeAlbumsApi(private val data: List<AlbumDto> = emptyList()) : AlbumsApi {
    override suspend fun list() = data
    override suspend fun get(id: String) = data.first { it.id == id }
    override suspend fun create(body: CreateAlbumRequest) = throw UnsupportedOperationException()
    override suspend fun rename(id: String, body: UpdateAlbumRequest) =
        throw UnsupportedOperationException()
    override suspend fun delete(id: String) = Response.success<Unit>(null)
    override suspend fun addFlower(id: String, body: AddFlowerToAlbumRequest) =
        throw UnsupportedOperationException()
    override suspend fun removeFlower(id: String, flowerId: String) =
        throw UnsupportedOperationException()
    override suspend fun setGroup(
        id: String,
        body: com.florapin.app.network.dto.SetAlbumGroupRequest,
    ) = throw UnsupportedOperationException()
    override suspend fun setPermissions(
        id: String,
        body: com.florapin.app.network.dto.SetAlbumPermissionsRequest,
    ) = throw UnsupportedOperationException()
}
