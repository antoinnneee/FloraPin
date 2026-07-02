package com.florapin.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Accès aux photos additionnelles des fleurs (NODE-107). */
@Dao
interface PhotoDao {

    @Query(
        "SELECT * FROM flower_photos WHERE flowerLocalId = :flowerLocalId " +
            "AND deletedAt IS NULL ORDER BY position ASC",
    )
    fun observeForFlower(flowerLocalId: Long): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM flower_photos WHERE id = :id")
    suspend fun getById(id: Long): PhotoEntity?

    @Query("SELECT * FROM flower_photos WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): PhotoEntity?

    @Query(
        "SELECT * FROM flower_photos WHERE flowerLocalId = :flowerLocalId " +
            "AND deletedAt IS NULL",
    )
    suspend fun forFlower(flowerLocalId: Long): List<PhotoEntity>

    /** Toutes les photos d'une fleur, y compris marquées supprimées (purge). */
    @Query("SELECT * FROM flower_photos WHERE flowerLocalId = :flowerLocalId")
    suspend fun allForFlower(flowerLocalId: Long): List<PhotoEntity>

    @Insert
    suspend fun insert(photo: PhotoEntity): Long

    @Update
    suspend fun update(photo: PhotoEntity)

    @Query("DELETE FROM flower_photos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM flower_photos")
    suspend fun deleteAll()

    // --- Synchronisation ---

    @Query("SELECT * FROM flower_photos WHERE syncState != 'SYNCED'")
    suspend fun pendingSync(): List<PhotoEntity>

    @Query(
        "UPDATE flower_photos SET serverId = :serverId, syncState = 'SYNCED' " +
            "WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String)

    /** Renseigne le chemin local après mise en cache d'une image distante. */
    @Query("UPDATE flower_photos SET imagePath = :path WHERE id = :id")
    suspend fun setImagePath(id: Long, path: String)

    // --- Upload d'image en souffrance (I9) ---

    /** Photos synchronisées dont l'upload d'image doit être retenté. */
    @Query(
        "SELECT * FROM flower_photos WHERE imagePendingUpload = 1 " +
            "AND serverId IS NOT NULL AND deletedAt IS NULL",
    )
    suspend fun pendingImageUploads(): List<PhotoEntity>

    /** Pose/lève le marqueur d'upload d'image en souffrance. */
    @Query("UPDATE flower_photos SET imagePendingUpload = :pending WHERE id = :id")
    suspend fun setImagePendingUpload(id: Long, pending: Boolean)
}
