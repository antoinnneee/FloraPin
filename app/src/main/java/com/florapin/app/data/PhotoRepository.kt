package com.florapin.app.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Persistance des photos additionnelles des fleurs (NODE-107) : ajout local,
 * observation, et helpers de synchronisation.
 */
class PhotoRepository(
    private val dao: PhotoDao,
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    fun photosOf(flowerLocalId: Long): Flow<List<PhotoEntity>> =
        dao.observeForFlower(flowerLocalId)

    suspend fun forFlower(flowerLocalId: Long): List<PhotoEntity> =
        dao.forFlower(flowerLocalId)

    /** Ajoute une photo capturée localement (à synchroniser). */
    suspend fun addLocalPhoto(flowerLocalId: Long, imagePath: String): Long {
        val existing = dao.forFlower(flowerLocalId)
        return dao.insert(
            PhotoEntity(
                flowerLocalId = flowerLocalId,
                imagePath = imagePath,
                position = existing.size,
                syncState = SyncState.PENDING.name,
            ),
        )
    }

    /** Marque une photo supprimée (propagée au prochain push), ou la retire. */
    suspend fun delete(photo: PhotoEntity) {
        if (photo.serverId == null) {
            dao.deleteById(photo.id)
        } else {
            dao.update(
                photo.copy(deletedAt = now(), syncState = SyncState.PENDING.name),
            )
        }
    }

    // --- Synchronisation (PhotoSyncEngine) ---

    suspend fun pendingSync(): List<PhotoEntity> = dao.pendingSync()

    suspend fun getById(id: Long): PhotoEntity? = dao.getById(id)

    suspend fun findByServerId(serverId: String): PhotoEntity? =
        dao.findByServerId(serverId)

    suspend fun insert(photo: PhotoEntity): Long = dao.insert(photo)

    suspend fun update(photo: PhotoEntity) = dao.update(photo)

    suspend fun markSynced(localId: Long, serverId: String) =
        dao.markSynced(localId, serverId)

    /** Toutes les photos d'une fleur, y compris marquées supprimées (purge). */
    suspend fun allForFlower(flowerLocalId: Long): List<PhotoEntity> =
        dao.allForFlower(flowerLocalId)

    /** Photos synchronisées dont l'upload d'image doit être retenté (I9). */
    suspend fun pendingImageUploads(): List<PhotoEntity> = dao.pendingImageUploads()

    /** Pose/lève le marqueur d'upload d'image en souffrance (I9). */
    suspend fun setImagePendingUpload(localId: Long, pending: Boolean) =
        dao.setImagePendingUpload(localId, pending)

    /** Renseigne le chemin local après mise en cache d'une image distante. */
    suspend fun cacheImagePath(localId: Long, path: String) =
        dao.setImagePath(localId, path)

    suspend fun hardDelete(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    companion object {
        fun from(context: Context): PhotoRepository =
            PhotoRepository(FloraDatabase.getInstance(context).photoDao())
    }
}
