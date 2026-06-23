package com.florapin.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Accès aux albums locaux et à leurs appartenances (NODE-102). */
@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: Long): AlbumEntity?

    @Query("SELECT * FROM albums WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE deletedAt IS NULL")
    suspend fun allActive(): List<AlbumEntity>

    @Insert
    suspend fun insert(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM albums")
    suspend fun deleteAllAlbums()

    // --- Synchronisation ---

    @Query("SELECT * FROM albums WHERE syncState != 'SYNCED'")
    suspend fun pendingSync(): List<AlbumEntity>

    @Query(
        "UPDATE albums SET serverId = :serverId, syncState = 'SYNCED', " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, updatedAt: Long)

    // --- Appartenances (cross-ref) ---

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCrossRef(ref: FlowerAlbumCrossRef)

    @Query("DELETE FROM flower_album_cross_ref WHERE albumId = :albumId")
    suspend fun clearMembers(albumId: Long)

    @Query(
        "DELETE FROM flower_album_cross_ref " +
            "WHERE albumId = :albumId AND flowerId = :flowerId",
    )
    suspend fun removeMember(albumId: Long, flowerId: Long)

    @Query("SELECT flowerId FROM flower_album_cross_ref WHERE albumId = :albumId")
    suspend fun memberFlowerIds(albumId: Long): List<Long>

    /** serverId des fleurs membres de l'album (celles déjà synchronisées). */
    @Query(
        "SELECT f.serverId FROM flowers f " +
            "INNER JOIN flower_album_cross_ref x ON x.flowerId = f.id " +
            "WHERE x.albumId = :albumId AND f.serverId IS NOT NULL",
    )
    suspend fun memberFlowerServerIds(albumId: Long): List<String>

    @Query("DELETE FROM flower_album_cross_ref")
    suspend fun deleteAllCrossRefs()
}
