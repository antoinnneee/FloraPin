package com.florapin.app.share

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.CreateShareRequest
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.network.dto.ShareDto
import com.florapin.app.network.dto.ShareToAllFriendsRequest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

/**
 * Tests UI Compose de la feuille de partage (NODE-71). On injecte un
 * [ShareViewModel] alimenté par des APIs factices (aucun réseau réel), sur le
 * modèle de `ShareViewModelTest`, et on pilote les interactions clés :
 * sélection d'un destinataire, recherche d'un ami hors raccourcis, création
 * ciblée / réseau, révocation.
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

    private fun submitButton() = compose.onNodeWithText("Partager")

    @Test
    fun selectingFriend_thenSubmit_createsTargetedShare() {
        val sharesApi = FakeSharesApi()
        val recents = FakeRecents()
        val vm = ShareViewModel(
            FakeFriendshipsApi(listOf(acceptedFriend("u1", "Alice"))),
            sharesApi,
            recents,
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
        // Le seul périmètre proposé est désormais la photo affichée.
        assertEquals("flower", req.scope)
        assertEquals("srv-1", req.flowerId)
        assertEquals(true, req.includeGps)
        // Le destinataire est mémorisé pour les prochains partages.
        assertEquals(listOf("u1"), recents.recentFriendIds())
    }

    @Test
    fun selectingAllFriends_submits_networkShare() {
        val sharesApi = FakeSharesApi()
        val vm = ShareViewModel(
            FakeFriendshipsApi(listOf(acceptedFriend("u1", "Alice"))),
            sharesApi,
            FakeRecents(),
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

    /**
     * Au-delà de [SharePreferences.RECENT_LIMIT] amis, les suivants ne sont plus
     * affichés en raccourci : ils restent atteignables via le bouton « … ».
     */
    @Test
    fun friendBeyondShortcuts_isReachableThroughSearch() {
        val sharesApi = FakeSharesApi()
        val friends = (1..6).map { acceptedFriend("u$it", "Ami $it") }
        val vm = ShareViewModel(FakeFriendshipsApi(friends), sharesApi, FakeRecents())
        compose.setContent {
            ShareFlowerSheet(flowerServerId = "srv-1", onDismiss = {}, viewModel = vm)
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Ami 1").fetchSemanticsNodes().isNotEmpty()
        }

        // « Ami 6 » est hors des 4 raccourcis.
        compose.onAllNodesWithText("Ami 6").assertCountEquals(0)

        // On filtre sur l'email pour que le texte saisi ne se confonde pas avec
        // le libellé du résultat (« Ami 6 »), seul nœud cliquable attendu.
        compose.onNodeWithContentDescription("Chercher un ami").performClick()
        compose.onNodeWithText("Nom ou email").performTextInput("u6@b.c")
        compose.onNodeWithText("Ami 6").performClick()

        submitButton().performClick()
        compose.waitUntil(timeoutMillis = 5_000) { sharesApi.created != null }
        assertEquals("u6", sharesApi.created!!.friendId)
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
                    scope = "flower",
                    flowerId = "srv-1",
                    includeGps = true,
                    createdAt = "2026-06-21T09:00:00Z",
                ),
            )
        }
        val vm = ShareViewModel(
            FakeFriendshipsApi(listOf(acceptedFriend("u1", "Alice"))),
            sharesApi,
            FakeRecents(),
        )
        compose.setContent {
            ShareFlowerSheet(flowerServerId = "srv-1", onDismiss = {}, viewModel = vm)
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
            body.flowerId, body.albumId, body.includeGps ?: true, "2026-06-21T09:00:00Z")
    }
    override suspend fun createForAllFriends(body: ShareToAllFriendsRequest): ShareDto {
        createdForAll = body
        return ShareDto("s1", "owner", null, "all_friends", body.scope,
            body.flowerId, body.albumId, body.includeGps ?: true, "2026-06-21T09:00:00Z")
    }
    override suspend fun listMine() = shares.toList()
    override suspend fun revoke(id: String): Response<Unit> {
        revoked = id
        return Response.success(null)
    }
    override suspend fun sharedWithMe() = emptyList<FlowerDto>()
}

private class FakeRecents(initial: List<String> = emptyList()) : RecentRecipientsStore {
    private val ids = initial.toMutableList()
    override fun recentFriendIds() = ids.toList()
    override fun rememberRecentFriend(friendId: String) {
        ids.remove(friendId)
        ids.add(0, friendId)
    }
}
