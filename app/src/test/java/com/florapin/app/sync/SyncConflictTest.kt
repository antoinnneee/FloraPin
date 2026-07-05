package com.florapin.app.sync

import com.florapin.app.data.FlowerDao
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.FlowerGeoTime
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.SyncState
import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.api.SyncApi
import com.florapin.app.network.dto.CreateFlowerRequest
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.ImageUrlResponse
import com.florapin.app.network.dto.SyncPullResponse
import com.florapin.app.network.dto.SyncPushItemResult
import com.florapin.app.network.dto.SyncPushRequest
import com.florapin.app.network.dto.UpdateFlowerRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** DAO minimal en mémoire pour ce test. */
private class MemDao : FlowerDao {
    val store = linkedMapOf<Long, FlowerEntity>()
    private var seq = 0L
    override fun observeAll(): Flow<List<FlowerEntity>> = flowOf(store.values.toList())
    override suspend fun getById(id: Long) = store[id]
    override suspend fun findByServerId(serverId: String) =
        store.values.find { it.serverId == serverId }
    override suspend fun allActive() = store.values.filter { it.deletedAt == null }
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
        val id = ++seq; store[id] = flower.copy(id = id); return id
    }
    override suspend fun update(flower: FlowerEntity) { store[flower.id] = flower }
    override suspend fun delete(flower: FlowerEntity) { store.remove(flower.id) }
    override suspend fun deleteAll() { store.clear() }
    override suspend fun pendingSync() =
        store.values.filter { it.syncState != SyncState.SYNCED.name }
    override suspend fun markSynced(
        id: Long,
        serverId: String,
        updatedAt: Long,
        expectedUpdatedAt: Long,
    ) {
        store[id]?.let {
            if (it.updatedAt != expectedUpdatedAt) {
                store[id] = it.copy(serverId = serverId)
            } else {
                store[id] = it.copy(serverId = serverId, syncState = SyncState.SYNCED.name)
            }
        }
    }
    override suspend fun markFailed(id: Long) {
        store[id]?.let { store[id] = it.copy(syncState = SyncState.FAILED.name) }
    }
    override suspend fun pendingImageUploads(): List<FlowerEntity> = emptyList()
    override suspend fun setImagePendingUpload(id: Long, pending: Boolean) {}
    override suspend fun setImagePath(id: Long, path: String) {
        store[id]?.let { store[id] = it.copy(imagePath = path) }
    }
    override suspend fun softDeleteByServerId(serverId: String, deletedAt: Long) {}
    override suspend fun countActive(): Int = 0
    override suspend fun countDistinctSpecies(): Int = 0
    override suspend fun geoTimes(): List<FlowerGeoTime> = emptyList()
    override suspend fun speciesCounts(): List<com.florapin.app.data.LocalSpeciesCount> =
        emptyList()
}

private class ConflictFlowersApi : FlowersApi {
    override suspend fun create(body: CreateFlowerRequest) = throw UnsupportedOperationException()
    override suspend fun list(species: String?, tag: String?) = emptyList<FlowerDto>()
    override suspend fun get(id: String) = throw UnsupportedOperationException()
    override suspend fun uploadImage(id: String, file: okhttp3.MultipartBody.Part) =
        throw UnsupportedOperationException()
    override suspend fun imageUrl(id: String) = ImageUrlResponse("u")
    override suspend fun update(id: String, body: UpdateFlowerRequest): FlowerDto =
        throw HttpException(Response.error<FlowerDto>(409, "".toResponseBody()))
    override suspend fun delete(id: String) = retrofit2.Response.success<Unit>(null)
}

private class EmptySyncApi : SyncApi {
    override suspend fun pull(since: String?) = SyncPullResponse("t", emptyList(), emptyList())
    override suspend fun push(body: SyncPushRequest) = emptyList<SyncPushItemResult>()
}

private class MemLastSync : LastSyncStore {
    private var v: String? = null
    override fun get() = v
    override fun set(value: String) { v = value }
}

class SyncConflictTest {

    @Test
    fun update409_marksSynced_serverWins() = runBlocking {
        val dao = MemDao()
        val repo = FlowerRepository(dao)
        val id = dao.insert(
            FlowerEntity(
                imagePath = "/p.jpg",
                createdAt = 1_000L,
                serverId = "srv-1",
                notes = "local",
                syncState = SyncState.PENDING.name,
                updatedAt = 2_000L,
            ),
        )

        val engine = SyncEngine(
            repository = repo,
            syncApi = EmptySyncApi(),
            flowersApi = ConflictFlowersApi(),
            uploadFlowerImage = { _, _ -> },
            lastSyncStore = MemLastSync(),
            now = { 9_000L },
        )

        engine.push()

        // 409 ⇒ serveur fait foi ⇒ marqué synchronisé (le pull réconciliera).
        assertEquals(SyncState.SYNCED.name, dao.store[id]!!.syncState)
    }
}
