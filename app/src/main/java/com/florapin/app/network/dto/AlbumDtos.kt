package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** Album tel que renvoyé par le backend (cf. backend/docs/API.md). */
@JsonClass(generateAdapter = true)
data class AlbumDto(
    val id: String,
    val ownerId: String,
    val name: String,
    /** Id stable côté client (null pour les albums créés avant l'anti-doublon). */
    val clientId: String? = null,
    /** Groupe collaboratif de rattachement (null = album solo). TÂCHE 7.1. */
    val groupId: String? = null,
    /** Régime de droits d'un album de groupe : 'open' | 'restricted'. */
    val permissionMode: String = "open",
    /** Le compte courant peut-il éditer cet album (calculé serveur) ? */
    val canEdit: Boolean = true,
    val flowerIds: List<String> = emptyList(),
    /** Droits « au cas par cas » (présent seulement pour un album de groupe). */
    val permissions: List<AlbumPermissionDto> = emptyList(),
    val createdAt: String,
)

/** Droit d'édition d'un membre sur un album (mode 'restricted'). */
@JsonClass(generateAdapter = true)
data class AlbumPermissionDto(
    val userId: String,
    val canEdit: Boolean,
)

@JsonClass(generateAdapter = true)
data class CreateAlbumRequest(
    val name: String,
    /** UUID stable du client : rend la création idempotente côté serveur. */
    val clientId: String,
    /** TÂCHE 7.1 — rattache à un groupe existant (le créateur doit en être membre). */
    val groupId: String? = null,
    /** TÂCHE 7.1 — « créer un album crée le groupe ». */
    val collaborative: Boolean? = null,
    /** Régime de droits initial d'un album de groupe ('open' par défaut). */
    val permissionMode: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateAlbumRequest(
    val name: String,
)

@JsonClass(generateAdapter = true)
data class AddFlowerToAlbumRequest(
    val flowerId: String,
)

/** Rattache/détache un album à un groupe (TÂCHE 7.1). */
@JsonClass(generateAdapter = true)
data class SetAlbumGroupRequest(
    /** Groupe cible, ou null pour détacher (album redevient solo). */
    val groupId: String? = null,
    val permissionMode: String? = null,
)

/** Configure les droits d'un album de groupe (TÂCHE 7.1). */
@JsonClass(generateAdapter = true)
data class SetAlbumPermissionsRequest(
    val mode: String,
    val entries: List<AlbumPermissionDto> = emptyList(),
)
