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
    val species: String? = null,
    val tags: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateFlowerRequest(
    val notes: String? = null,
    val visibility: String? = null,
    val takenAt: String? = null,
    val species: String? = null,
    val tags: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class PresignedUpload(
    val url: String,
    val method: String,
    val expiresIn: Int,
)

@JsonClass(generateAdapter = true)
data class FlowerDto(
    val id: String,
    val ownerId: String,
    val imageUrl: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Double? = null,
    val takenAt: String,
    val notes: String,
    val visibility: String,
    val species: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
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
