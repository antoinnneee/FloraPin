package com.florapin.app.sync

import com.florapin.app.data.FlowerDao
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerGeoTime
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.SyncState
import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.api.SyncApi
import com.florapin.app.network.dto.CreateFlowerRequest
import com.florapin.app.network.dto.CreateFlowerResponse
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.ImageUrlResponse
import com.florapin.app.network.dto.PresignedUpload
import com.florapin.app.network.dto.SyncPullResponse
import com.florapin.app.network.dto.SyncPushItemResult
import com.florapin.app.network.dto.SyncPushRequest
import com.florapin.app.network.dto.UpdateFlowerRequest
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeDao : FlowerDao {
    val store = linkedMapOf<Long, FlowerEntity>()
    private var seq = 0L

    override fun observeAll(): Flow<List<FlowerEntity>> = flowOf(store.values.toList())
    override suspend fun getById(id: Long) = store[id]
    override suspend fun findByServerId(serverId: String) =
        store.values.find { it.serverId == serverId }
    override suspend fun allActive() = store.values.filter { it.deletedAt == null }
    override suspend fun recentActive(limit: Int) =
        store.values.filter { it.deletedAt == null }.sortedByDescending { it.createdAt }.take(limit)
    override suspend fun findLocalTwin(createdAt: Long) =
        store.values.find {
            it.createdAt == createdAt && it.imagePath.isNotEmpty() && it.deletedAt == null
        }
    override fun observeById(id: Long): Flow<FlowerEntity?> = flowOf(store[id])
    override fun observeBySpecies(
        speciesId: String?,
        scientificName: String?,
    ): Flow<List<FlowerEntity>> = flowOf(emptyList())

    override suspend fun insert(flower: FlowerEntity): Long {
        val id = ++seq
        store[id] = flower.copy(id = id)
        return id
    }
    override suspend fun update(flower: FlowerEntity) {
        store[flower.id] = flower
    }
    override suspend fun delete(flower: FlowerEntity) {
        store.remove(flower.id)
    }
    override suspend fun deleteAll() {
        store.clear()
    }
    override suspend fun pendingSync() =
        store.values.filter { it.syncState != SyncState.SYNCED.name }
    override suspend fun markSynced(
        id: Long,
        serverId: String,
        updatedAt: Long,
        expectedUpdatedAt: Long,
    ) {
        store[id]?.let {
            // Reflète le SQL réel : serverId toujours persisté ; l'état ne passe
            // SYNCED que si la ligne n'a pas bougé depuis la lecture du push.
            val untouched = it.updatedAt == expectedUpdatedAt
            store[id] = it.copy(
                serverId = serverId,
                syncState = if (untouched) SyncState.SYNCED.name else it.syncState,
                updatedAt = if (untouched) updatedAt else it.updatedAt,
            )
        }
    }
    override suspend fun markFailed(id: Long) {
        store[id]?.let { store[id] = it.copy(syncState = SyncState.FAILED.name) }
    }
    override suspend fun pendingImageUploads() =
        store.values.filter {
            it.imagePendingUpload && it.serverId != null && it.deletedAt == null
        }
    override suspend fun setImagePendingUpload(id: Long, pending: Boolean) {
        store[id]?.let { store[id] = it.copy(imagePendingUpload = pending) }
    }
    override suspend fun setImagePath(id: Long, path: String) {
        store[id]?.let { store[id] = it.copy(imagePath = path) }
    }
    override suspend fun softDeleteByServerId(serverId: String, deletedAt: Long) {
        store.values.find { it.serverId == serverId }?.let {
            store[it.id] = it.copy(deletedAt = deletedAt, syncState = SyncState.SYNCED.name)
        }
    }
    override suspend fun countActive(): Int = 0
    override suspend fun countDistinctSpecies(): Int = 0
    override suspend fun geoTimes(): List<FlowerGeoTime> = emptyList()
    override suspend fun speciesCounts(): List<com.florapin.app.data.LocalSpeciesCount> =
        emptyList()
}

private class FakeSyncApi(
    var pushResults: List<SyncPushItemResult> = emptyList(),
    var pullResponse: SyncPullResponse = SyncPullResponse("t", emptyList(), emptyList()),
) : SyncApi {
    var lastPushSize = 0
    override suspend fun pull(since: String?): SyncPullResponse = pullResponse
    override suspend fun push(body: SyncPushRequest): List<SyncPushItemResult> {
        lastPushSize = body.items.size
        return pushResults
    }
}

private class FakeFlowersApi : FlowersApi {
    var lastUpdateBody: UpdateFlowerRequest? = null
    val deleted = mutableListOf<String>()
    /** Si non null, `delete` lève cette exception au lieu de réussir. */
    var deleteError: Exception? = null
    override suspend fun create(body: CreateFlowerRequest) =
        throw UnsupportedOperationException()
    override suspend fun list(species: String?, tag: String?) = emptyList<FlowerDto>()
    override suspend fun get(id: String) = throw UnsupportedOperationException()
    override suspend fun uploadImage(
        id: String,
        file: okhttp3.MultipartBody.Part,
    ) = dto(id, "2026-06-21T10:00:00Z")
    override suspend fun imageUrl(id: String) = ImageUrlResponse("u")
    override suspend fun update(id: String, body: UpdateFlowerRequest): FlowerDto {
        lastUpdateBody = body
        return dto(id, "2026-06-21T10:00:00Z")
    }
    override suspend fun delete(id: String): retrofit2.Response<Unit> {
        deleteError?.let { throw it }
        deleted += id
        return retrofit2.Response.success<Unit>(null)
    }
}

private class FakeLastSync : LastSyncStore {
    var value: String? = null
    override fun get() = value
    override fun set(value: String) {
        this.value = value
    }
}

private fun dto(id: String, updatedAt: String) = FlowerDto(
    id = id,
    ownerId = "o",
    imageUrl = "https://x/$id.jpg",
    takenAt = "2026-06-21T09:00:00Z",
    notes = "",
    visibility = "private",
    createdAt = "2026-06-21T09:00:00Z",
    updatedAt = updatedAt,
)

class SyncEngineTest {

    @Test
    fun push_uploadsImageAndMarksSynced() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val localId = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                syncState = SyncState.PENDING.name,
                updatedAt = 1_000L,
            ),
        )

        val syncApi = FakeSyncApi(
            pushResults = listOf(
                SyncPushItemResult(
                    localId = localId.toString(),
                    flower = dto("srv-9", "2026-06-21T10:00:00Z"),
                    upload = PresignedUpload("https://upload", "PUT", 600),
                ),
            ),
        )
        val uploads = mutableListOf<Pair<String, File>>()
        val engine = SyncEngine(
            repository = repo,
            syncApi = syncApi,
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { serverId, file -> uploads += serverId to file },
            lastSyncStore = FakeLastSync(),
            now = { 5_000L },
        )

        engine.push()

        assertEquals(1, syncApi.lastPushSize)
        assertEquals("srv-9", uploads.single().first)
        val synced = dao.store[localId]!!
        assertEquals("srv-9", synced.serverId)
        assertEquals(SyncState.SYNCED.name, synced.syncState)
    }

    /**
     * Le partage automatique s'applique à la fleur qui vient d'obtenir son id
     * serveur, et seulement après l'envoi de son image (sinon les amis verraient
     * une fleur sans photo).
     */
    @Test
    fun push_autoSharesCreatedFlowerAfterImageUpload() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val localId = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                syncState = SyncState.PENDING.name,
                updatedAt = 1_000L,
            ),
        )
        val syncApi = FakeSyncApi(
            pushResults = listOf(
                SyncPushItemResult(
                    localId = localId.toString(),
                    flower = dto("srv-9", "2026-06-21T10:00:00Z"),
                    upload = PresignedUpload("https://upload", "PUT", 600),
                ),
            ),
        )
        val events = mutableListOf<String>()
        val engine = SyncEngine(
            repository = repo,
            syncApi = syncApi,
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { serverId, _ -> events += "upload:$serverId" },
            lastSyncStore = FakeLastSync(),
            autoShareFlower = { serverId -> events += "share:$serverId" },
            now = { 5_000L },
        )

        engine.push()

        assertEquals(listOf("upload:srv-9", "share:srv-9"), events)
    }

    /** Un partage automatique en échec ne doit pas faire échouer la sync. */
    @Test
    fun push_autoShareFailure_doesNotBreakSync() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val localId = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                syncState = SyncState.PENDING.name,
                updatedAt = 1_000L,
            ),
        )
        val syncApi = FakeSyncApi(
            pushResults = listOf(
                SyncPushItemResult(
                    localId = localId.toString(),
                    flower = dto("srv-9", "2026-06-21T10:00:00Z"),
                    upload = PresignedUpload("https://upload", "PUT", 600),
                ),
            ),
        )
        val engine = SyncEngine(
            repository = repo,
            syncApi = syncApi,
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
            autoShareFlower = { throw IllegalStateException("réseau") },
            now = { 5_000L },
        )

        engine.push()

        val synced = dao.store[localId]!!
        assertEquals("srv-9", synced.serverId)
        assertEquals(SyncState.SYNCED.name, synced.syncState)
    }

    @Test
    fun pull_appliesDeletesAndPersistsCursor() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val id = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                serverId = "srv-1",
                syncState = SyncState.SYNCED.name,
                updatedAt = 1_000L,
            ),
        )
        val store = FakeLastSync()
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(
                pullResponse = SyncPullResponse(
                    serverTime = "2026-06-21T12:00:00Z",
                    flowers = emptyList(),
                    deletedIds = listOf("srv-1"),
                ),
            ),
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = store,
            now = { 9_000L },
        )

        engine.pull()

        assertNotNull(dao.store[id]!!.deletedAt)
        assertEquals("2026-06-21T12:00:00Z", store.value)
    }

    @Test
    fun pull_insertsUnknownRemoteFlower() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(
                pullResponse = SyncPullResponse(
                    serverTime = "2026-06-21T12:00:00Z",
                    flowers = listOf(dto("srv-remote", "2026-06-21T11:00:00Z")),
                    deletedIds = emptyList(),
                ),
            ),
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
        )

        engine.pull()

        val inserted = dao.store.values.single { it.serverId == "srv-remote" }
        assertEquals("", inserted.imagePath)
        assertEquals("https://x/srv-remote.jpg", inserted.remoteImageUrl)
        assertEquals(SyncState.SYNCED.name, inserted.syncState)
    }

    @Test
    fun pull_cachesRemoteImageLocally() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val cached = mutableListOf<Pair<String, String>>()
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(
                pullResponse = SyncPullResponse(
                    serverTime = "2026-06-21T12:00:00Z",
                    flowers = listOf(dto("srv-remote", "2026-06-21T11:00:00Z")),
                    deletedIds = emptyList(),
                ),
            ),
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
            // Simule un téléchargement réussi : retourne un chemin local stable.
            cacheRemoteImage = { serverId, url ->
                cached += serverId to url
                "/local/$serverId.img"
            },
        )

        engine.pull()

        // L'image distante a été téléchargée et imagePath pointe vers le fichier
        // local : l'affichage ne dépend plus de l'expiration de l'URL présignée.
        assertEquals(listOf("srv-remote" to "https://x/srv-remote.jpg"), cached)
        val inserted = dao.store.values.single { it.serverId == "srv-remote" }
        assertEquals("/local/srv-remote.img", inserted.imagePath)
    }

    @Test
    fun pull_keepsRemoteUrlWhenCacheFails() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(
                pullResponse = SyncPullResponse(
                    serverTime = "2026-06-21T12:00:00Z",
                    flowers = listOf(dto("srv-remote", "2026-06-21T11:00:00Z")),
                    deletedIds = emptyList(),
                ),
            ),
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
            // Échec de téléchargement (URL expirée, réseau) : on retombe sur l'URL.
            cacheRemoteImage = { _, _ -> null },
        )

        engine.pull()

        val inserted = dao.store.values.single { it.serverId == "srv-remote" }
        assertEquals("", inserted.imagePath)
        assertEquals("https://x/srv-remote.jpg", inserted.remoteImageUrl)
    }

    @Test
    fun pull_skipsServerDuplicateOfLocalCapture() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        // takenAt du helper dto() = 2026-06-21T09:00:00Z.
        val takenAtMillis = java.time.Instant.parse("2026-06-21T09:00:00Z").toEpochMilli()
        // Capture locale déjà synchronisée (image présente) à cette date.
        dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = takenAtMillis,
                serverId = "srv-original",
                syncState = SyncState.SYNCED.name,
                updatedAt = takenAtMillis,
            ),
        )
        val flowersApi = FakeFlowersApi()
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(
                pullResponse = SyncPullResponse(
                    serverTime = "2026-06-21T12:00:00Z",
                    // Orphelin serveur : même date de capture, autre serverId.
                    flowers = listOf(dto("srv-orphan", "2026-06-21T11:00:00Z")),
                    deletedIds = emptyList(),
                ),
            ),
            flowersApi = flowersApi,
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
        )

        engine.pull()

        // Aucun doublon inséré et l'orphelin est supprimé côté serveur.
        assertNull(dao.store.values.find { it.serverId == "srv-orphan" })
        assertEquals(1, dao.store.size)
        assertEquals(listOf("srv-orphan"), flowersApi.deleted)
    }

    @Test
    fun pull_reconcilesKnownFlower() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val id = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                serverId = "srv-2",
                notes = "ancien",
                syncState = SyncState.SYNCED.name,
            ),
        )
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(
                pullResponse = SyncPullResponse(
                    serverTime = "2026-06-21T12:00:00Z",
                    flowers = listOf(dto("srv-2", "2026-06-21T11:00:00Z").copy(notes = "neuf")),
                    deletedIds = emptyList(),
                ),
            ),
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
        )

        engine.pull()

        assertEquals("neuf", dao.store[id]!!.notes)
        assertNull(dao.store[id]!!.deletedAt)
    }

    @Test
    fun push_updateCarriesSpeciesAndSpeciesId() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                serverId = "srv-up",
                species = "Rosa canina",
                speciesId = "sp-1",
                tags = listOf("jardin"),
                syncState = SyncState.PENDING.name,
                updatedAt = 1_000L,
            ),
        )
        val flowersApi = FakeFlowersApi()
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(),
            flowersApi = flowersApi,
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
            now = { 5_000L },
        )

        engine.push()

        val body = flowersApi.lastUpdateBody!!
        assertEquals("Rosa canina", body.species)
        assertEquals("sp-1", body.speciesId)
        assertEquals(listOf("jardin"), body.tags)
    }

    @Test
    fun push_updateCarriesVisibilityAndFeedGps() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                serverId = "srv-vis",
                visibility = "friends",
                feedIncludeGps = false,
                syncState = SyncState.PENDING.name,
                updatedAt = 1_000L,
            ),
        )
        val flowersApi = FakeFlowersApi()
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(),
            flowersApi = flowersApi,
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
            now = { 5_000L },
        )

        engine.push()

        val body = flowersApi.lastUpdateBody!!
        assertEquals("friends", body.visibility)
        assertEquals(false, body.feedIncludeGps)
    }

    @Test
    fun push_deletePropagated_thenPurgesLocalRow() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        // Fleur synchronisée puis supprimée localement (soft-delete, C3).
        val id = dao.insert(
            FlowerEntity(
                imagePath = "",
                createdAt = 1_000L,
                serverId = "srv-del",
                syncState = SyncState.PENDING.name,
                updatedAt = 2_000L,
                deletedAt = 2_000L,
            ),
        )
        val flowersApi = FakeFlowersApi()
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(),
            flowersApi = flowersApi,
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
            now = { 5_000L },
        )

        engine.push()

        // La suppression a été poussée, puis la ligne locale purgée.
        assertEquals(listOf("srv-del"), flowersApi.deleted)
        assertNull(dao.store[id])
    }

    @Test
    fun push_delete404_alsoPurgesLocalRow() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val id = dao.insert(
            FlowerEntity(
                imagePath = "",
                createdAt = 1_000L,
                serverId = "srv-gone",
                syncState = SyncState.PENDING.name,
                updatedAt = 2_000L,
                deletedAt = 2_000L,
            ),
        )
        val flowersApi = FakeFlowersApi().apply {
            deleteError = retrofit2.HttpException(
                retrofit2.Response.error<Unit>(404, "".toResponseBody()),
            )
        }
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(),
            flowersApi = flowersApi,
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
            now = { 5_000L },
        )

        engine.push()

        // Déjà supprimée côté serveur : même issue qu'un succès, la ligne part.
        assertNull(dao.store[id])
    }

    @Test
    fun push_imageUploadFailure_flagsPendingUpload_thenRetriesNextSync() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val localId = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                syncState = SyncState.PENDING.name,
                updatedAt = 1_000L,
            ),
        )
        val syncApi = FakeSyncApi(
            pushResults = listOf(
                SyncPushItemResult(
                    localId = localId.toString(),
                    flower = dto("srv-9", "2026-06-21T10:00:00Z"),
                    upload = PresignedUpload("https://upload", "PUT", 600),
                ),
            ),
        )
        var failUpload = true
        val uploads = mutableListOf<String>()
        val engine = SyncEngine(
            repository = repo,
            syncApi = syncApi,
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { serverId, _ ->
                if (failUpload) error("réseau coupé") else uploads += serverId
            },
            lastSyncStore = FakeLastSync(),
            now = { 5_000L },
        )

        engine.push()

        // La fleur est synchronisée (serverId persisté) mais l'image, en échec,
        // est marquée en souffrance au lieu d'être perdue (I9).
        val afterFail = dao.store[localId]!!
        assertEquals("srv-9", afterFail.serverId)
        assertEquals(SyncState.SYNCED.name, afterFail.syncState)
        assertEquals(true, afterFail.imagePendingUpload)

        // Sync suivant, réseau rétabli : l'upload est retenté et le marqueur levé.
        failUpload = false
        engine.push()

        assertEquals(listOf("srv-9"), uploads)
        assertEquals(false, dao.store[localId]!!.imagePendingUpload)
    }

    @Test
    fun pull_doesNotOverwriteLocallyPendingFlower() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        // Édition locale non poussée (PENDING) : le pull ne doit pas l'écraser.
        val id = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                serverId = "srv-2",
                notes = "édition locale",
                syncState = SyncState.PENDING.name,
                updatedAt = 2_000L,
            ),
        )
        val engine = SyncEngine(
            repository = repo,
            syncApi = FakeSyncApi(
                pullResponse = SyncPullResponse(
                    serverTime = "2026-06-21T12:00:00Z",
                    flowers = listOf(
                        dto("srv-2", "2026-06-21T11:00:00Z").copy(notes = "serveur"),
                    ),
                    deletedIds = emptyList(),
                ),
            ),
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
        )

        engine.pull()

        // L'édition locale survit ; elle sera poussée (last-write-wins au push).
        assertEquals("édition locale", dao.store[id]!!.notes)
        assertEquals(SyncState.PENDING.name, dao.store[id]!!.syncState)
    }

    @Test
    fun push_concurrentEdit_keepsFlowerPendingButPersistsServerId() = runBlocking {
        val dao = FakeDao()
        val repo = FlowerRepository(dao)
        val localId = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                notes = "v1",
                syncState = SyncState.PENDING.name,
                updatedAt = 1_000L,
            ),
        )
        // SyncApi qui simule une édition utilisateur PENDANT le push (entre la
        // lecture de la file et le markSynced).
        val racingSyncApi = object : SyncApi {
            override suspend fun pull(since: String?) =
                SyncPullResponse("t", emptyList(), emptyList())
            override suspend fun push(body: SyncPushRequest): List<SyncPushItemResult> {
                dao.store[localId] = dao.store[localId]!!.copy(
                    notes = "v2 (édition concurrente)",
                    updatedAt = 9_999L,
                    syncState = SyncState.PENDING.name,
                )
                return listOf(
                    SyncPushItemResult(
                        localId = localId.toString(),
                        flower = dto("srv-9", "2026-06-21T10:00:00Z"),
                        upload = PresignedUpload("https://upload", "PUT", 600),
                    ),
                )
            }
        }
        val engine = SyncEngine(
            repository = repo,
            syncApi = racingSyncApi,
            flowersApi = FakeFlowersApi(),
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
            now = { 5_000L },
        )

        engine.push()

        val after = dao.store[localId]!!
        // Le serverId est persisté (anti-doublon)…
        assertEquals("srv-9", after.serverId)
        // … mais l'édition concurrente n'est pas écrasée : la fleur reste
        // PENDING et sera poussée au prochain sync.
        assertEquals(SyncState.PENDING.name, after.syncState)
        assertEquals("v2 (édition concurrente)", after.notes)
        assertEquals(9_999L, after.updatedAt)
    }
}
