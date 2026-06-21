package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs de synchronisation. */
@JsonClass(generateAdapter = true)
data class SyncPullResponse(
    val serverTime: String,
    val flowers: List<FlowerDto>,
    val deletedIds: List<String>,
)

@JsonClass(generateAdapter = true)
data class PushItem(
    val localId: String,
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
data class SyncPushRequest(
    val items: List<PushItem>,
)

@JsonClass(generateAdapter = true)
data class SyncPushItemResult(
    val localId: String,
    val flower: FlowerDto,
    val upload: PresignedUpload,
)
