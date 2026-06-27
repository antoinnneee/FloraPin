package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs des fleurs (alignés sur l'API REST). */
@JsonClass(generateAdapter = true)
data class CreateFlowerRequest(
    val takenAt: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Double? = null,
    val notes: String? = null,
    val visibility: String? = null,
    /** Diffusion GPS au flux d'amis (NODE-137). */
    val feedIncludeGps: Boolean? = null,
    val species: String? = null,
    val tags: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateFlowerRequest(
    val notes: String? = null,
    val visibility: String? = null,
    /** Diffusion GPS au flux d'amis (NODE-137). */
    val feedIncludeGps: Boolean? = null,
    val takenAt: String? = null,
    val species: String? = null,
    /** FK référentiel (NODE-128) : rattache la fleur à une fiche espèce. */
    val speciesId: String? = null,
    val tags: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class PresignedUpload(
    val url: String,
    val method: String,
    val expiresIn: Int,
)

@JsonClass(generateAdapter = true)
data class PhotoDto(
    val id: String,
    val url: String,
    /** URL présignée de la miniature WebP (preview), ou null si non réencodée. */
    val thumbnailUrl: String? = null,
    val position: Int,
    val isCover: Boolean,
)

@JsonClass(generateAdapter = true)
data class FlowerDto(
    val id: String,
    val ownerId: String,
    val imageUrl: String,
    /** URL présignée de la miniature de couverture (preview), ou null. */
    val thumbnailUrl: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Double? = null,
    val takenAt: String,
    val notes: String,
    val visibility: String,
    val species: String? = null,
    /** FK référentiel (NODE-124), null si non rapprochée. */
    val speciesId: String? = null,
    /** Espèce résolue depuis le référentiel (NODE-125), null si non rapprochée. */
    val speciesRef: SpeciesRefDto? = null,
    /** Fleur en attente d'identification collaborative (NODE-133/134). */
    val needsIdentification: Boolean = false,
    /** Diffusion GPS au flux d'amis quand visibility='friends' (NODE-137). */
    val feedIncludeGps: Boolean = true,
    /** Nombre de cœurs reçus (NODE-139/140). */
    val likeCount: Int = 0,
    /** Le spectateur courant a-t-il liké cette fleur (NODE-139/140). */
    val likedByMe: Boolean = false,
    val tags: List<String> = emptyList(),
    val photos: List<PhotoDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

/** Photos ordonnées (couverture d'abord, puis par position). */
private fun FlowerDto.orderedPhotos(): List<PhotoDto> =
    photos.sortedWith(compareByDescending<PhotoDto> { it.isCover }.thenBy { it.position })

/**
 * URLs pleine résolution de toutes les photos de la fleur (couverture d'abord).
 * Repli sur l'image de couverture seule si la liste `photos` est vide (anciennes
 * fleurs ou DTO non enrichi).
 */
fun FlowerDto.fullPhotoUrls(): List<String> =
    orderedPhotos().map { it.url }.ifEmpty { listOf(imageUrl) }

/** Versions légères (miniature WebP si dispo) des mêmes photos, pour les listes. */
fun FlowerDto.previewPhotoUrls(): List<String> =
    orderedPhotos().map { it.thumbnailUrl ?: it.url }
        .ifEmpty { listOf(thumbnailUrl ?: imageUrl) }

@JsonClass(generateAdapter = true)
data class AddPhotoResponse(
    val photo: PhotoDto,
    val upload: PresignedUpload,
)

@JsonClass(generateAdapter = true)
data class ReorderPhotosRequest(
    val photoIds: List<String>,
)

@JsonClass(generateAdapter = true)
data class CreateFlowerResponse(
    val flower: FlowerDto,
    val upload: PresignedUpload,
)

@JsonClass(generateAdapter = true)
data class ImageUrlResponse(
    val imageUrl: String,
)
