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
    /** Nombre total de réactions reçues, tous types confondus (NODE-139/140). */
    val likeCount: Int = 0,
    /** Le spectateur courant a-t-il réagi à cette fleur (NODE-139/140). */
    val likedByMe: Boolean = false,
    /** Décompte par type de réaction (codes), types absents omis (TÂCHE 3.5). */
    val reactionCounts: Map<String, Int> = emptyMap(),
    /** Réaction posée par le spectateur courant, ou null (TÂCHE 3.5). */
    val myReaction: String? = null,
    /** Nombre de commentaires reçus (TÂCHE 3.3). */
    val commentCount: Int = 0,
    val tags: List<String> = emptyList(),
    val photos: List<PhotoDto> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Applique optimiste le passage à la réaction [code] (ou null = retrait) sur la
 * fleur (TÂCHE 3.5) : ajuste `myReaction`, `likedByMe`, le total et le décompte
 * par type. Changer de type conserve le total (retire l'ancien, ajoute le nouveau).
 */
fun FlowerDto.withReaction(code: String?): FlowerDto {
    val old = myReaction
    if (old == code) return this
    val counts = reactionCounts.toMutableMap()
    if (old != null) {
        val n = (counts[old] ?: 1) - 1
        if (n > 0) counts[old] = n else counts.remove(old)
    }
    if (code != null) counts[code] = (counts[code] ?: 0) + 1
    val delta = (if (code != null) 1 else 0) - (if (old != null) 1 else 0)
    return copy(
        myReaction = code,
        likedByMe = code != null,
        likeCount = (likeCount + delta).coerceAtLeast(0),
        reactionCounts = counts,
    )
}

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
