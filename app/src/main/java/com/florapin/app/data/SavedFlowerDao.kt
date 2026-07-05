package com.florapin.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Accès à « Ma sélection » : fleurs d'amis enregistrées localement (TÂCHE 3.11). */
@Dao
interface SavedFlowerDao {

    /** Snapshots enregistrés, du plus récent au plus ancien. */
    @Query("SELECT * FROM saved_flowers ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedFlowerEntity>>

    /** Ids serveur enregistrés (pour l'état « épinglé » des cartes du feed). */
    @Query("SELECT serverId FROM saved_flowers")
    fun observeSavedIds(): Flow<List<String>>

    @Query("SELECT * FROM saved_flowers WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): SavedFlowerEntity?

    /** Tous les snapshots (pour le nettoyage des fichiers en cache). */
    @Query("SELECT * FROM saved_flowers")
    suspend fun all(): List<SavedFlowerEntity>

    /** Ré-enregistrer une fleur remplace son snapshot (idempotent). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(saved: SavedFlowerEntity): Long

    @Query("DELETE FROM saved_flowers WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Query("DELETE FROM saved_flowers")
    suspend fun deleteAll()
}
