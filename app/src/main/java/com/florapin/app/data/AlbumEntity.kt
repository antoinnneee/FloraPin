package com.florapin.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Album local (NODE-102) : regroupement nommé de fleurs. Champs de sync alignés
 * sur [FlowerEntity] (serverId, syncState, updatedAt, deletedAt).
 */
@Entity(
    tableName = "albums",
    indices = [Index(value = ["clientId"], unique = true)],
)
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Id serveur (null tant que l'album n'a pas été poussé). */
    val serverId: String? = null,

    /**
     * Identifiant stable généré localement à la création (UUID). Envoyé au
     * serveur pour rendre la création idempotente : un re-push (réponse perdue,
     * crash après le POST) retombe sur l'album existant au lieu d'en créer un
     * doublon. Sert aussi de filet au pull pour rattacher un album local resté
     * sans serverId. Unique localement.
     */
    val clientId: String,

    val name: String,

    /** Propriétaire serveur (null pour un album créé localement non synchronisé). */
    val ownerId: String? = null,

    val createdAt: Long,
    val updatedAt: Long,
    val syncState: String = SyncState.PENDING.name,
    val deletedAt: Long? = null,
)

/**
 * Appartenance fleur ↔ album (n..n), par identifiants LOCAUX. Reflète la table
 * serveur `flower_albums`.
 */
@Entity(
    tableName = "flower_album_cross_ref",
    primaryKeys = ["albumId", "flowerId"],
    indices = [Index("flowerId")],
)
data class FlowerAlbumCrossRef(
    val albumId: Long,
    val flowerId: Long,
)
