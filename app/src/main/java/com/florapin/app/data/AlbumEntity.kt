package com.florapin.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Album local (NODE-102) : regroupement nommé de fleurs. Champs de sync alignés
 * sur [FlowerEntity] (serverId, syncState, updatedAt, deletedAt).
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Id serveur (null tant que l'album n'a pas été poussé). */
    val serverId: String? = null,

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
