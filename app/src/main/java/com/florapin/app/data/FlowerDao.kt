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

    /** Flux des fleurs non supprimées, des plus récentes aux plus anciennes. */
    @Query("SELECT * FROM flowers WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<FlowerEntity>>

    @Query("SELECT * FROM flowers WHERE id = :id")
    suspend fun getById(id: Long): FlowerEntity?

    /**
     * Flux des fleurs (non supprimées) d'une espèce donnée (NODE-151) : celles
     * rattachées au référentiel ([speciesId]) ou dont le texte libre correspond
     * au nom scientifique ([scientificName]).
     */
    @Query(
        "SELECT * FROM flowers WHERE deletedAt IS NULL AND " +
            "(speciesId = :speciesId OR species = :scientificName) " +
            "ORDER BY createdAt DESC",
    )
    fun observeBySpecies(
        speciesId: String?,
        scientificName: String?,
    ): Flow<List<FlowerEntity>>

    @Query("SELECT * FROM flowers WHERE serverId = :serverId LIMIT 1")
    suspend fun findByServerId(serverId: String): FlowerEntity?

    /**
     * Cherche une capture locale (image présente, non supprimée) à la date de
     * capture donnée. Sert à détecter, au pull, qu'une fleur distante « inconnue »
     * fait en réalité doublon avec une de nos fleurs déjà synchronisée.
     */
    @Query(
        "SELECT * FROM flowers WHERE createdAt = :createdAt AND imagePath != '' " +
            "AND deletedAt IS NULL LIMIT 1",
    )
    suspend fun findLocalTwin(createdAt: Long): FlowerEntity?

    /** Flux d'une fleur non supprimée (émet null si supprimée/inexistante). */
    @Query("SELECT * FROM flowers WHERE id = :id AND deletedAt IS NULL")
    fun observeById(id: Long): Flow<FlowerEntity?>

    /** Insère une fleur et renvoie son identifiant généré. */
    @Insert
    suspend fun insert(flower: FlowerEntity): Long

    @Update
    suspend fun update(flower: FlowerEntity)

    @Delete
    suspend fun delete(flower: FlowerEntity)

    /** Purge toutes les fleurs (changement de compte — NODE-93). */
    @Query("DELETE FROM flowers")
    suspend fun deleteAll()

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

    /** Suppression logique d'une fleur identifiée par son id serveur (pull). */
    @Query(
        "UPDATE flowers SET deletedAt = :deletedAt, updatedAt = :deletedAt, " +
            "syncState = 'SYNCED' WHERE serverId = :serverId",
    )
    suspend fun softDeleteByServerId(serverId: String, deletedAt: Long)
}
