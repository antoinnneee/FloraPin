package com.florapin.app.detail

import com.florapin.app.network.api.CommentsApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.dto.CreateCommentRequest
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.FlowerCommentDto
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.network.dto.UpdateCommentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

private class FakeCommentsApi : CommentsApi {
    var lastPosted: String? = null
    override suspend fun post(flowerId: String, body: CreateCommentRequest): FlowerCommentDto {
        lastPosted = body.body
        return FlowerCommentDto(
            id = "c1",
            flowerId = flowerId,
            authoredBy = "me",
            body = body.body,
            createdAt = "2026-07-05T10:00:00Z",
        )
    }

    override suspend fun list(flowerId: String): List<FlowerCommentDto> = emptyList()

    var lastEdited: String? = null
    override suspend fun update(
        flowerId: String,
        commentId: String,
        body: UpdateCommentRequest,
    ): FlowerCommentDto {
        lastEdited = body.body
        return FlowerCommentDto(
            id = commentId,
            flowerId = flowerId,
            authoredBy = "me",
            body = body.body,
            canEdit = true,
            createdAt = "2026-07-05T10:00:00Z",
            editedAt = "2026-07-05T11:00:00Z",
        )
    }

    override suspend fun delete(flowerId: String, commentId: String): Response<Unit> =
        Response.success(Unit)
}

/** API amis en mémoire : renvoie une liste figée d'amitiés (fake de test). */
private class FakeFriendshipsApi(
    private val friendships: List<FriendshipDto>,
) : FriendshipsApi {
    override suspend fun list(): List<FriendshipDto> = friendships
    override suspend fun request(body: CreateFriendshipRequest): FriendshipDto =
        throw UnsupportedOperationException()
    override suspend fun requestById(
        body: com.florapin.app.network.dto.AddFriendByIdRequest,
    ): FriendshipDto = throw UnsupportedOperationException()
    override suspend fun accept(id: String): FriendshipDto =
        throw UnsupportedOperationException()
    override suspend fun remove(id: String): Response<Unit> = Response.success(Unit)
}

private fun acceptedFriend(id: String, name: String): FriendshipDto = FriendshipDto(
    id = "friendship-$id",
    status = "accepted",
    direction = "outgoing",
    user = FriendUserDto(id = id, displayName = name, email = "$name@ex.fr"),
    createdAt = "2026-07-05T10:00:00Z",
)

/** Store de brouillons en mémoire, indexé par fleur (fake de test). */
private class FakeDraftStore : CommentDraftStore {
    val drafts = mutableMapOf<String, String>()
    override fun load(flowerServerId: String): String = drafts[flowerServerId] ?: ""
    override fun save(flowerServerId: String, draft: String) {
        if (draft.isEmpty()) drafts.remove(flowerServerId) else drafts[flowerServerId] = draft
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `le brouillon saisi est persiste par fleur`() = runTest(dispatcher) {
        val store = FakeDraftStore()
        val vm = CommentsViewModel(FakeCommentsApi(), store)
        vm.bind("flower-1")
        advanceUntilIdle()

        vm.updateDraft("Belle rose")

        assertEquals("Belle rose", store.drafts["flower-1"])
    }

    @Test
    fun `un nouveau bind restaure le brouillon persiste`() = runTest(dispatcher) {
        val store = FakeDraftStore().apply { drafts["flower-1"] = "En cours…" }
        val vm = CommentsViewModel(FakeCommentsApi(), store)

        vm.bind("flower-1")
        advanceUntilIdle()

        assertEquals("En cours…", vm.state.value.draft)
    }

    @Test
    fun `chaque fleur a son propre brouillon`() = runTest(dispatcher) {
        val store = FakeDraftStore()
        val vm = CommentsViewModel(FakeCommentsApi(), store)

        vm.bind("flower-1")
        advanceUntilIdle()
        vm.updateDraft("brouillon A")

        vm.bind("flower-2")
        advanceUntilIdle()
        // La fleur 2 n'a pas de brouillon : le champ repart vide.
        assertEquals("", vm.state.value.draft)
        assertEquals("brouillon A", store.drafts["flower-1"])
    }

    @Test
    fun `l'envoi reussi efface le brouillon persiste`() = runTest(dispatcher) {
        val store = FakeDraftStore()
        val vm = CommentsViewModel(FakeCommentsApi(), store)
        vm.bind("flower-1")
        advanceUntilIdle()
        vm.updateDraft("À envoyer")

        vm.submit()
        advanceUntilIdle()

        assertEquals("", vm.state.value.draft)
        assertEquals(null, store.drafts["flower-1"])
    }

    @Test
    fun `l'edition remplace le commentaire et marque editedAt`() = runTest(dispatcher) {
        val api = FakeCommentsApi()
        val vm = CommentsViewModel(api, FakeDraftStore())
        vm.bind("flower-1")
        advanceUntilIdle()
        // Poste un commentaire pour l'avoir dans la liste.
        vm.updateDraft("Fôte")
        vm.submit()
        advanceUntilIdle()

        val posted = vm.state.value.comments.single()
        vm.startEdit(posted)
        vm.updateEditDraft("Faute")
        vm.submitEdit()
        advanceUntilIdle()

        assertEquals("Faute", api.lastEdited)
        val edited = vm.state.value.comments.single()
        assertEquals("Faute", edited.body)
        assertEquals("2026-07-05T11:00:00Z", edited.editedAt)
        assertEquals(null, vm.state.value.editingId)
    }

    @Test
    fun `une saisie @ propose les amis correspondants`() = runTest(dispatcher) {
        val friendsApi = FakeFriendshipsApi(
            listOf(
                acceptedFriend("u-marie", "Marie"),
                acceptedFriend("u-marc", "Marc"),
                acceptedFriend("u-bob", "Bob"),
            ),
        )
        val vm = CommentsViewModel(FakeCommentsApi(), FakeDraftStore(), friendsApi)
        vm.bind("flower-1")
        advanceUntilIdle()

        vm.updateDraft("Regarde @mar")

        assertEquals(
            listOf("Marie", "Marc"),
            vm.state.value.mentionSuggestions.map { it.displayName },
        )
    }

    @Test
    fun `selectMention encode l'id dans le brouillon et ferme les suggestions`() =
        runTest(dispatcher) {
            val marie = acceptedFriend("u-marie", "Marie")
            val friendsApi = FakeFriendshipsApi(listOf(marie))
            val store = FakeDraftStore()
            val vm = CommentsViewModel(FakeCommentsApi(), store, friendsApi)
            vm.bind("flower-1")
            advanceUntilIdle()
            vm.updateDraft("Regarde @mar")

            val newText = vm.selectMention(marie.user)

            // Le brouillon encode l'IDENTIFIANT (pas le nom) — robuste au renommage.
            assertEquals("Regarde @[u-marie] ", newText)
            assertEquals("Regarde @[u-marie] ", vm.state.value.draft)
            assertEquals("Regarde @[u-marie] ", store.drafts["flower-1"])
            assertEquals(emptyList<FriendUserDto>(), vm.state.value.mentionSuggestions)
        }
}
