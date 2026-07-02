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
import okhttp3.ResponseBody.Companion.toResponseBody
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
    override suspend fun allForFlower(flowerLocalId: Long) =
        store.values.filter { it.flowerLocalId == flowerLocalId }
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
    override suspend fun setImagePath(id: Long, path: String) {
        store[id]?.let { store[id] = it.copy(imagePath = path) }
    }
    override suspend fun pendingImageUploads() =
        store.values.filter {
            it.imagePendingUpload && it.serverId != null && it.deletedAt == null
        }
    override suspend fun setImagePendingUpload(id: Long, pending: Boolean) {
        store[id]?.let { store[id] = it.copy(imagePendingUpload = pending) }
    }
}

private class FakePhotosApi : PhotosApi {
    var uploadedFrom: String? = null
    var added = 0
    val uploaded = mutableListOf<Pair<String, String>>()
    val removed = mutableListOf<Pair<String, String>>()
    override suspend fun add(flowerId: String): AddPhotoResponse {
        added++
        return AddPhotoResponse(
            photo = PhotoDto("srv-photo-$added", "url", position = added, isCover = false),
            upload = PresignedUpload("https://up/$added", "PUT", 600),
        )
    }
    override suspend fun uploadImage(
        flowerId: String,
        photoId: String,
        file: okhttp3.MultipartBody.Part,
    ): PhotoDto {
        uploaded.add(flowerId to photoId)
        return PhotoDto(photoId, "url", position = 0, isCover = false)
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
        var uploadedPhotoId: String? = null
        val engine = PhotoSyncEngine(repo, api, uploadPhotoImage = { _, photoServerId, _ ->
            uploadedPhotoId = photoServerId
        })

        repo.addLocalPhoto(flowerLocalId = 1L, imagePath = "/p.jpg")

        engine.push { localId -> if (localId == 1L) "srv-flower" else null }

        val photo = dao.store.values.single()
        assertEquals(SyncState.SYNCED.name, photo.syncState)
        assertEquals("srv-photo-1", photo.serverId)
        assertEquals("srv-photo-1", uploadedPhotoId)
    }

    @Test
    fun push_skipsPhotosOfUnsyncedFlower() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        val engine = PhotoSyncEngine(repo, FakePhotosApi(), uploadPhotoImage = { _, _, _ -> })

        repo.addLocalPhoto(flowerLocalId = 2L, imagePath = "/p.jpg")
        engine.push { null } // fleur pas encore synchronisée

        assertEquals(SyncState.PENDING.name, dao.store.values.single().syncState)
    }

    @Test
    fun reconcile_insertsAdditionalServerPhotos_ignoringCover() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        val engine = PhotoSyncEngine(repo, FakePhotosApi(), uploadPhotoImage = { _, _, _ -> })

        engine.reconcile(
            flowerLocalId = 5L,
            remote = listOf(
                PhotoDto("cover-1", "u0", position = 0, isCover = true),
                PhotoDto("extra-1", "u1", position = 1, isCover = false),
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
        val engine = PhotoSyncEngine(repo, FakePhotosApi(), uploadPhotoImage = { _, _, _ -> })

        dao.insert(
            PhotoEntity(flowerLocalId = 5L, serverId = "gone", remoteUrl = "u",
                position = 0, syncState = SyncState.SYNCED.name),
        )

        engine.reconcile(5L, emptyList())

        assertEquals(0, dao.forFlower(5L).size)
    }

    @Test
    fun push_uploadFailure_flagsPendingUpload_thenRetriesNextSync() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        var failUpload = true
        val uploaded = mutableListOf<String>()
        val engine = PhotoSyncEngine(
            repo,
            FakePhotosApi(),
            uploadPhotoImage = { _, photoServerId, _ ->
                if (failUpload) error("réseau coupé") else uploaded += photoServerId
            },
        )
        val id = repo.addLocalPhoto(flowerLocalId = 1L, imagePath = "/p.jpg")

        engine.push { "srv-flower" }

        // Photo synchronisée (serverId connu) mais image en souffrance (I9).
        val afterFail = dao.store[id]!!
        assertEquals(SyncState.SYNCED.name, afterFail.syncState)
        assertEquals(true, afterFail.imagePendingUpload)

        // Sync suivant : l'upload est retenté et le marqueur levé.
        failUpload = false
        engine.push { "srv-flower" }

        assertEquals(listOf(afterFail.serverId), uploaded)
        assertEquals(false, dao.store[id]!!.imagePendingUpload)
    }

    @Test
    fun push_404onAdd_purgesPhotoInsteadOfRetryingForever() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        // La fleur n'existe plus côté serveur : add répond 404.
        val api = object : PhotosApi by FakePhotosApi() {
            override suspend fun add(flowerId: String): AddPhotoResponse =
                throw retrofit2.HttpException(
                    Response.error<AddPhotoResponse>(404, "".toResponseBody()),
                )
        }
        val engine = PhotoSyncEngine(repo, api, uploadPhotoImage = { _, _, _ -> })
        repo.addLocalPhoto(flowerLocalId = 1L, imagePath = "/p.jpg")

        engine.push { "srv-flower-disparue" }

        // Erreur permanente : la photo est purgée, pas de retry infini (I10).
        assertEquals(0, dao.store.size)
    }

    @Test
    fun push_transientError_keepsPhotoPending() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        val api = object : PhotosApi by FakePhotosApi() {
            override suspend fun add(flowerId: String): AddPhotoResponse =
                throw java.io.IOException("réseau coupé")
        }
        val engine = PhotoSyncEngine(repo, api, uploadPhotoImage = { _, _, _ -> })
        val id = repo.addLocalPhoto(flowerLocalId = 1L, imagePath = "/p.jpg")

        engine.push { "srv-flower" }

        // Erreur transitoire : la photo reste en attente pour le prochain sync.
        assertEquals(SyncState.PENDING.name, dao.store[id]!!.syncState)
    }

    @Test
    fun purgeForFlower_removesAllRowsIncludingDeleted() = runBlocking {
        val dao = MemPhotoDao()
        val repo = PhotoRepository(dao) { 100L }
        val engine = PhotoSyncEngine(repo, FakePhotosApi(), uploadPhotoImage = { _, _, _ -> })

        dao.insert(PhotoEntity(flowerLocalId = 7L, imagePath = "/a.jpg"))
        dao.insert(
            PhotoEntity(flowerLocalId = 7L, serverId = "s", deletedAt = 10L),
        )
        dao.insert(PhotoEntity(flowerLocalId = 8L, imagePath = "/autre.jpg"))

        engine.purgeForFlower(7L)

        // Toutes les photos de la fleur 7 sont parties, celles de la 8 restent.
        assertEquals(0, dao.allForFlower(7L).size)
        assertEquals(1, dao.allForFlower(8L).size)
    }
}
