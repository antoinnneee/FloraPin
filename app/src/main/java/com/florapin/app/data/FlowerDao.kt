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

    /** Toutes les fleurs non supprimées (dump de sauvegarde locale). */
    @Query("SELECT * FROM flowers WHERE deletedAt IS NULL ORDER BY createdAt ASC")
    suspend fun allActive(): List<FlowerEntity>

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

    /**
     * Marque une fleur synchronisée après un push. Le `serverId` est TOUJOURS
     * persisté (anti-doublon : sans lui, un re-push créerait une fleur « grise »
     * côté serveur). En revanche, `syncState`/`updatedAt` ne basculent en SYNCED
     * que si la ligne n'a pas été modifiée entre-temps (updatedAt encore égal à
     * [expectedUpdatedAt]) : une édition utilisateur survenue pendant le push
     * reste ainsi PENDING et sera poussée au prochain sync.
     */
    @Query(
        "UPDATE flowers SET serverId = :serverId, " +
            "syncState = CASE WHEN updatedAt = :expectedUpdatedAt " +
            "THEN 'SYNCED' ELSE syncState END, " +
            "updatedAt = CASE WHEN updatedAt = :expectedUpdatedAt " +
            "THEN :updatedAt ELSE updatedAt END " +
            "WHERE id = :id",
    )
    suspend fun markSynced(
        id: Long,
        serverId: String,
        updatedAt: Long,
        expectedUpdatedAt: Long,
    )

    @Query("UPDATE flowers SET syncState = 'FAILED' WHERE id = :id")
    suspend fun markFailed(id: Long)

    // --- Upload d'image en souffrance (I9) ---

    /** Fleurs synchronisées dont l'upload d'image doit être retenté. */
    @Query(
        "SELECT * FROM flowers WHERE imagePendingUpload = 1 " +
            "AND serverId IS NOT NULL AND deletedAt IS NULL",
    )
    suspend fun pendingImageUploads(): List<FlowerEntity>

    /** Pose/lève le marqueur d'upload d'image en souffrance. */
    @Query("UPDATE flowers SET imagePendingUpload = :pending WHERE id = :id")
    suspend fun setImagePendingUpload(id: Long, pending: Boolean)

    /**
     * Renseigne le chemin local de l'image après mise en cache d'une image
     * distante (NODE-53). N'altère PAS le syncState : il ne s'agit pas d'une
     * modification utilisateur à repousser, juste d'un cache d'affichage.
     */
    @Query("UPDATE flowers SET imagePath = :path WHERE id = :id")
    suspend fun setImagePath(id: Long, path: String)

    /** Suppression logique d'une fleur identifiée par son id serveur (pull). */
    @Query(
        "UPDATE flowers SET deletedAt = :deletedAt, updatedAt = :deletedAt, " +
            "syncState = 'SYNCED' WHERE serverId = :serverId",
    )
    suspend fun softDeleteByServerId(serverId: String, deletedAt: Long)
}
