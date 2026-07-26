package com.florapin.app.albums

import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.MemAlbumDao
import com.florapin.app.data.SyncState
import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.dto.AddFlowerToAlbumRequest
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.CreateAlbumRequest
import com.florapin.app.network.dto.SetAlbumCoverRequest
import com.florapin.app.network.dto.SetAlbumGroupRequest
import com.florapin.app.network.dto.SetAlbumPermissionsRequest
import com.florapin.app.network.dto.UpdateAlbumRequest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * AlbumsApi de test : seule `create` est utilisée par le ViewModel, les autres
 * routes échouent bruyamment si un jour elles sont sollicitées par erreur.
 */
private class FakeAlbumsApi(
    private val onCreate: suspend (CreateAlbumRequest) -> AlbumDto,
) : AlbumsApi {
    /** Dernière requête de création reçue (assertions sur clientId/collaborative). */
    var lastCreate: CreateAlbumRequest? = null

    override suspend fun create(body: CreateAlbumRequest): AlbumDto {
        lastCreate = body
        return onCreate(body)
    }

    override suspend fun list(): List<AlbumDto> = unsupported()
    override suspend fun get(id: String): AlbumDto = unsupported()
    override suspend fun rename(id: String, body: UpdateAlbumRequest): AlbumDto = unsupported()
    override suspend fun delete(id: String): Response<Unit> = unsupported()
    override suspend fun addFlower(id: String, body: AddFlowerToAlbumRequest): AlbumDto =
        unsupported()
    override suspend fun removeFlower(id: String, flowerId: String): AlbumDto = unsupported()
    override suspend fun setCover(id: String, body: SetAlbumCoverRequest): AlbumDto =
        unsupported()
    override suspend fun setGroup(id: String, body: SetAlbumGroupRequest): AlbumDto =
        unsupported()
    override suspend fun setPermissions(
        id: String,
        body: SetAlbumPermissionsRequest,
    ): AlbumDto = unsupported()

    private fun unsupported(): Nothing =
        throw AssertionError("Route non attendue dans ce test.")
}

private fun albumDto(
    id: String = "srv-1",
    name: String = "Balade",
    clientId: String? = "cli-1",
    groupId: String? = "grp-1",
) = AlbumDto(
    id = id,
    ownerId = "me",
    name = name,
    clientId = clientId,
    groupId = groupId,
    createdAt = "2026-07-20T10:00:00Z",
)

private fun flower(id: Long) = FlowerEntity(id = id, imagePath = "/p/$id.jpg", createdAt = id)

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        dao: MemAlbumDao,
        api: AlbumsApi = FakeAlbumsApi { albumDto() },
    ) = AlbumsViewModel(AlbumRepository(dao) { 100L }, api)

    // --- Création locale (device-first) ---

    @Test
    fun create_persistsPendingAlbum() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        vm(dao).create("  Printemps  ")
        advanceUntilIdle()

        val album = dao.albums.values.single()
        // Le nom est nettoyé avant persistance.
        assertEquals("Printemps", album.name)
        assertEquals(SyncState.PENDING.name, album.syncState)
    }

    @Test
    fun create_ignoresBlankName() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        vm(dao).create("   ")
        advanceUntilIdle()

        assertTrue(dao.albums.isEmpty())
    }

    // --- Création collaborative (réseau requis) ---

    @Test
    fun createCollaborative_insertsSyncedAlbumFromServer() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val api = FakeAlbumsApi { albumDto(id = "srv-9", name = "Sortie botanique") }
        val viewModel = vm(dao, api)

        viewModel.createCollaborative("  Sortie botanique  ")
        advanceUntilIdle()

        val album = dao.albums.values.single()
        // L'album revient déjà synchronisé du serveur : pas de re-push au prochain cycle.
        assertEquals("srv-9", album.serverId)
        assertEquals(SyncState.SYNCED.name, album.syncState)
        assertEquals("grp-1", album.groupId)
        assertNull(viewModel.message.value)

        // Le nom est nettoyé et le drapeau collaboratif transmis au serveur.
        assertEquals("Sortie botanique", api.lastCreate?.name)
        assertEquals(true, api.lastCreate?.collaborative)
        // clientId non vide : rend la création idempotente côté serveur.
        assertTrue(api.lastCreate?.clientId?.isNotBlank() == true)
    }

    @Test
    fun createCollaborative_offline_reportsMessageAndCreatesNothing() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val api = FakeAlbumsApi { throw IOException("réseau indisponible") }
        val viewModel = vm(dao, api)

        viewModel.createCollaborative("Sortie")
        advanceUntilIdle()

        // Device-first : un album collaboratif ne peut pas naître hors-ligne.
        assertTrue(dao.albums.isEmpty())
        assertNotNull(viewModel.message.value)

        viewModel.clearMessage()
        assertNull(viewModel.message.value)
    }

    @Test
    fun createCollaborative_ignoresBlankName() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val api = FakeAlbumsApi { throw AssertionError("aucun appel réseau attendu") }
        vm(dao, api).createCollaborative("  ")
        advanceUntilIdle()

        assertTrue(dao.albums.isEmpty())
    }

    // --- Renommage / suppression ---

    @Test
    fun rename_trimsAndMarksPending() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val repo = AlbumRepository(dao) { 100L }
        val id = repo.create("Avant")
        dao.albums[id] = dao.albums[id]!!.copy(syncState = SyncState.SYNCED.name)

        vm(dao).rename(dao.albums[id]!!, "  Après  ")
        advanceUntilIdle()

        assertEquals("Après", dao.albums[id]!!.name)
        assertEquals(SyncState.PENDING.name, dao.albums[id]!!.syncState)
    }

    @Test
    fun rename_ignoresBlankName() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val id = AlbumRepository(dao) { 100L }.create("Avant")

        vm(dao).rename(dao.albums[id]!!, "   ")
        advanceUntilIdle()

        assertEquals("Avant", dao.albums[id]!!.name)
    }

    @Test
    fun delete_unsyncedAlbum_removesItLocally() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val id = AlbumRepository(dao) { 100L }.create("Tmp")

        vm(dao).delete(dao.albums[id]!!)
        advanceUntilIdle()

        assertTrue(dao.albums.isEmpty())
    }

    // --- Rattachement de fleurs ---

    @Test
    fun addFlowersToAlbum_addsEachMembershipOnce() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val id = AlbumRepository(dao) { 100L }.create("Balade")

        // Le doublon (7) doit être absorbé : l'appartenance est un ensemble.
        vm(dao).addFlowersToAlbum(id, listOf(7L, 8L, 7L))
        advanceUntilIdle()

        assertEquals(setOf(id to 7L, id to 8L), dao.refs)
    }

    @Test
    fun addFlowersToAlbum_ignoresEmptySelection() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val id = AlbumRepository(dao) { 100L }.create("Balade")

        vm(dao).addFlowersToAlbum(id, emptyList())
        advanceUntilIdle()

        assertTrue(dao.refs.isEmpty())
    }

    @Test
    fun addFlowerToAlbum_addsSingleMembership() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val id = AlbumRepository(dao) { 100L }.create("Balade")

        vm(dao).addFlowerToAlbum(id, 42L)
        advanceUntilIdle()

        assertEquals(setOf(id to 42L), dao.refs)
    }

    // --- Couvertures vivantes ---

    @Test
    fun summaries_pairEachAlbumWithItsFlowers() = runTest(dispatcher) {
        val dao = MemAlbumDao()
        val repo = AlbumRepository(dao) { 100L }
        val balade = repo.create("Balade")
        val jardin = repo.create("Jardin")
        dao.flowersByAlbum[balade] = listOf(flower(1), flower(2))
        dao.flowersByAlbum[jardin] = emptyList()

        val viewModel = vm(dao)
        // stateIn(WhileSubscribed) n'emet qu'avec un collecteur actif.
        backgroundScope.launch { viewModel.summaries.collect {} }
        advanceUntilIdle()

        val summaries = viewModel.summaries.value.associateBy { it.album.id }
        assertEquals(2, summaries.size)
        assertEquals(listOf(1L, 2L), summaries[balade]!!.flowers.map { it.id })
        assertTrue(summaries[jardin]!!.flowers.isEmpty())
    }

    @Test
    fun summaries_isEmptyWithoutAlbums() = runTest(dispatcher) {
        val viewModel = vm(MemAlbumDao())
        // stateIn(WhileSubscribed) n'emet qu'avec un collecteur actif.
        backgroundScope.launch { viewModel.summaries.collect {} }
        advanceUntilIdle()

        assertTrue(viewModel.summaries.value.isEmpty())
    }
}
