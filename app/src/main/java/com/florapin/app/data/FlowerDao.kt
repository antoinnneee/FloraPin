package com.florapin.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Accès aux fleurs persistées. */
@Dao
interface FlowerDao {

    /** Flux de toutes les fleurs, des plus récentes aux plus anciennes. */
    @Query("SELECT * FROM flowers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FlowerEntity>>

    @Query("SELECT * FROM flowers WHERE id = :id")
    suspend fun getById(id: Long): FlowerEntity?

    /** Flux d'une fleur (émet null si elle est supprimée). */
    @Query("SELECT * FROM flowers WHERE id = :id")
    fun observeById(id: Long): Flow<FlowerEntity?>

    /** Insère une fleur et renvoie son identifiant généré. */
    @Insert
    suspend fun insert(flower: FlowerEntity): Long

    @Update
    suspend fun update(flower: FlowerEntity)

    @Delete
    suspend fun delete(flower: FlowerEntity)

    // --- Synchronisation (NODE-43) ---

    /** Fleurs en attente d'envoi (créées/maj hors-ligne). */
    @Query("SELECT * FROM flowers WHERE syncState != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun pendingSync(): List<FlowerEntity>

    @Query(
        "UPDATE flowers SET serverId = :serverId, syncState = 'SYNCED', " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, updatedAt: Long)

    @Query("UPDATE flowers SET syncState = 'FAILED' WHERE id = :id")
    suspend fun markFailed(id: Long)
}
