package com.florapin.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Accès aux paliers de badges « collection » débloqués localement (TÂCHE 5.3). */
@Dao
interface BadgeDao {

    /** Tous les paliers débloqués, observés en continu (pour la grille Badges). */
    @Query("SELECT * FROM badges ORDER BY unlockedAt DESC")
    fun observeAll(): Flow<List<BadgeEntity>>

    /** Tous les paliers débloqués (lecture ponctuelle, pour le recalcul). */
    @Query("SELECT * FROM badges")
    suspend fun all(): List<BadgeEntity>

    /** Paliers pas encore « vus » (à célébrer). */
    @Query("SELECT * FROM badges WHERE seen = 0 ORDER BY unlockedAt ASC")
    suspend fun unseen(): List<BadgeEntity>

    /** Nombre de paliers enregistrés (0 = aucune exécution encore faite). */
    @Query("SELECT COUNT(*) FROM badges")
    suspend fun count(): Int

    /**
     * Insère les paliers nouvellement débloqués. `REPLACE` rend le recalcul
     * idempotent : un palier déjà présent (même `badgeId`/`tier`) est réécrit à
     * l'identique sans erreur — l'appelant ne passe donc que des paliers neufs.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(badges: List<BadgeEntity>)

    /** Marque tous les paliers comme vus (célébrations consommées). */
    @Query("UPDATE badges SET seen = 1 WHERE seen = 0")
    suspend fun markAllSeen()

    /** Marque un palier précis comme vu. */
    @Query("UPDATE badges SET seen = 1 WHERE badgeId = :badgeId AND tier = :tier")
    suspend fun markSeen(badgeId: String, tier: Int)

    /** Purge tous les badges (changement de compte — NODE-93). */
    @Query("DELETE FROM badges")
    suspend fun deleteAll()
}
