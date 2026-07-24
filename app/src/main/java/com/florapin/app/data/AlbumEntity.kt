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

    /**
     * Groupe collaboratif de rattachement (TÂCHE 7.1). Null = album solo/privé
     * (comportement historique). Un album de groupe est visible/éditable par les
     * membres selon [permissionMode].
     */
    val groupId: String? = null,

    /**
     * Régime de droits d'un album de groupe : 'open' (tout membre édite) ou
     * 'restricted' (au cas par cas). Sans effet pour un album solo.
     */
    val permissionMode: String = "open",

    /**
     * Le compte courant peut-il éditer cet album ? Calculé par le serveur et
     * mémorisé pour dégrader proprement hors-ligne. Vrai par défaut pour un album
     * local non encore synchronisé (le créateur en est propriétaire).
     */
    val canEdit: Boolean = true,

    /** Fleur locale choisie comme couverture, ou null pour la première de l'album. */
    val coverFlowerId: Long? = null,

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
