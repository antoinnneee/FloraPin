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
    val flowerIds: List<String> = emptyList(),
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class CreateAlbumRequest(
    val name: String,
    /** UUID stable du client : rend la création idempotente côté serveur. */
    val clientId: String,
)

@JsonClass(generateAdapter = true)
data class UpdateAlbumRequest(
    val name: String,
)

@JsonClass(generateAdapter = true)
data class AddFlowerToAlbumRequest(
    val flowerId: String,
)
