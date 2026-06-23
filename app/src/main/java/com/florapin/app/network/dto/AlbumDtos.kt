package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** Album tel que renvoyé par le backend (cf. backend/docs/API.md). */
@JsonClass(generateAdapter = true)
data class AlbumDto(
    val id: String,
    val ownerId: String,
    val name: String,
    val flowerIds: List<String> = emptyList(),
    val createdAt: String,
)
