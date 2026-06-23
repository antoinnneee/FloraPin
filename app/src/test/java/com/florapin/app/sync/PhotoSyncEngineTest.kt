package com.florapin.app.sync

import com.florapin.app.data.PhotoDao
import com.florapin.app.data.PhotoEntity
import com.florapin.app.data.PhotoRepository
import com.florapin.app.data.SyncState
import com.florapin.app.network.api.PhotosApi
import com.florapin.app.network.dto.AddPhotoResponse
import com.florapin.app.network.dto.PhotoDto
import com.florapin.app.network.dto.PresignedUpload
import com.florapin.app.network.dto.ReorderPhotosRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class MemPhotoDao : PhotoDao {
    val store = linkedMapOf<Long, PhotoEntity>()
    private var seq = 0L
    override fun observeForFlower(flowerLocalId: Long): Flow<List<PhotoEntity>> =
        flowOf(store.values.filter { it.flowerLocalId == flowerLocalId })
    override suspend fun getById(id: Long) = store[id]
    override suspend fun findByServerId(serverId: String) =
        store.values.find { it.serverId == serverId }
    override suspend fun forFlower(flowerLocalId: Long) =
        store.values.filter { it.flowerLocalId == flowerLocalId && it.deletedAt == null }
    override suspend fun insert(photo: PhotoEntity): Long {
        val id = ++seq; store[id] = photo.copy(id = id); return id
    }
    override suspend fun update(photo: PhotoEntity) { store[photo.id] = photo }
    override suspend fun deleteById(id: Long) { store.remove(id) }
    override suspend fun deleteAll() { store.clear() }
    override suspend fun pendingSync() =
        store.values.filter { it.syncState != SyncState.SYNCED.name }
    override suspend fun markSynced(id: Long, serverId: String) {
        store[id]?.let { store[id] = it.copy(serverId = serverId, syncState = SyncState.SYNCED.name) }
    }
}

private class FakePhotosApi : PhotosApi {
    var uploadedFrom: String? = null
    var added = 0
    val removed = mutableListOf<Pair<String, String>>()
    override suspend fun add(flowerId: String): AddPhotoResponse {
        added++
        return AddPhotoResponse(
            photo = PhotoDto("srv-photo-$added", "url", added, false),
            upload = PresignedUpload("https://up/$added", "PUT", 600),
        )
    }
    override suspend fun remove(flowerId: String, photoId: String): Response<Unit> {
        removed.add(flowerId to photoId)
        return Response.success(null)
    }
    override suspend fun reorder(flowerId: String, body: ReorderPhotosRequest) =
        emptyList<PhotoDto>()
}

class PhotoSyncEngineTest {

    @Test
    fun push_uploadsNewLocalPhoto_andMarksSynced() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        val api = FakePhotosApi()
        var uploaded: String? = null
        val engine = PhotoSyncEngine(repo, api) { url, _ -> uploaded = url }

        repo.addLocalPhoto(flowerLocalId = 1L, imagePath = "/p.jpg")

        engine.push { localId -> if (localId == 1L) "srv-flower" else null }

        val photo = dao.store.values.single()
        assertEquals(SyncState.SYNCED.name, photo.syncState)
        assertEquals("srv-photo-1", photo.serverId)
        assertEquals("https://up/1", uploaded)
    }

    @Test
    fun push_skipsPhotosOfUnsyncedFlower() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        val engine = PhotoSyncEngine(repo, FakePhotosApi()) { _, _ -> }

        repo.addLocalPhoto(flowerLocalId = 2L, imagePath = "/p.jpg")
        engine.push { null } // fleur pas encore synchronisée

        assertEquals(SyncState.PENDING.name, dao.store.values.single().syncState)
    }

    @Test
    fun reconcile_insertsAdditionalServerPhotos_ignoringCover() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        val engine = PhotoSyncEngine(repo, FakePhotosApi()) { _, _ -> }

        engine.reconcile(
            flowerLocalId = 5L,
            remote = listOf(
                PhotoDto("cover-1", "u0", 0, true),
                PhotoDto("extra-1", "u1", 1, false),
            ),
        )

        val photos = dao.forFlower(5L)
        assertEquals(1, photos.size)
        assertEquals("extra-1", photos.single().serverId)
        assertNull(photos.single().imagePath.ifEmpty { null })
        assertNotNull(photos.single().remoteUrl)
    }

    @Test
    fun reconcile_removesSyncedPhotoAbsentFromServer() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        val engine = PhotoSyncEngine(repo, FakePhotosApi()) { _, _ -> }

        dao.insert(
            PhotoEntity(flowerLocalId = 5L, serverId = "gone", remoteUrl = "u",
                position = 0, syncState = SyncState.SYNCED.name),
        )

        engine.reconcile(5L, emptyList())

        assertEquals(0, dao.forFlower(5L).size)
    }
}
