package com.florapin.app.sync

import com.florapin.app.data.FlowerDao
import com.florapin.app.data.FlowerEntity
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
    override suspend fun markSynced(id: Long, serverId: String, updatedAt: Long) {
        store[id]?.let {
            store[id] = it.copy(
                serverId = serverId,
                syncState = SyncState.SYNCED.name,
                updatedAt = updatedAt,
            )
        }
    }
    override suspend fun markFailed(id: Long) {
        store[id]?.let { store[id] = it.copy(syncState = SyncState.FAILED.name) }
    }
    override suspend fun softDeleteByServerId(serverId: String, deletedAt: Long) {
        store.values.find { it.serverId == serverId }?.let {
            store[it.id] = it.copy(deletedAt = deletedAt, syncState = SyncState.SYNCED.name)
        }
    }
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
    override suspend fun create(body: CreateFlowerRequest) =
        throw UnsupportedOperationException()
    override suspend fun list(species: String?, tag: String?) = emptyList<FlowerDto>()
    override suspend fun get(id: String) = throw UnsupportedOperationException()
    override suspend fun imageUrl(id: String) = ImageUrlResponse("u")
    override suspend fun update(id: String, body: UpdateFlowerRequest): FlowerDto {
        lastUpdateBody = body
        return dto(id, "2026-06-21T10:00:00Z")
    }
    override suspend fun delete(id: String) = retrofit2.Response.success<Unit>(null)
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
            uploadImage = { url, file -> uploads += url to file },
            lastSyncStore = FakeLastSync(),
            now = { 5_000L },
        )

        engine.push()

        assertEquals(1, syncApi.lastPushSize)
        assertEquals("https://upload", uploads.single().first)
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
            uploadImage = { _, _ -> },
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
            uploadImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
        )

        engine.pull()

        val inserted = dao.store.values.single { it.serverId == "srv-remote" }
        assertEquals("", inserted.imagePath)
        assertEquals("https://x/srv-remote.jpg", inserted.remoteImageUrl)
        assertEquals(SyncState.SYNCED.name, inserted.syncState)
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
            uploadImage = { _, _ -> },
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
            uploadImage = { _, _ -> },
            lastSyncStore = FakeLastSync(),
            now = { 5_000L },
        )

        engine.push()

        val body = flowersApi.lastUpdateBody!!
        assertEquals("Rosa canina", body.species)
        assertEquals("sp-1", body.speciesId)
        assertEquals(listOf("jardin"), body.tags)
    }
}
