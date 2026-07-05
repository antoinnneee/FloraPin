package com.florapin.app.detail

import com.florapin.app.network.api.CommentsApi
import com.florapin.app.network.dto.CreateCommentRequest
import com.florapin.app.network.dto.FlowerCommentDto
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

    override suspend fun delete(flowerId: String, commentId: String): Response<Unit> =
        Response.success(Unit)
}

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
}
