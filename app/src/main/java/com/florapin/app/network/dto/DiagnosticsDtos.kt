package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** Rapport technique volontairement envoyé depuis Profil > Configuration. */
@JsonClass(generateAdapter = true)
data class CreateClientLogRequest(
    val appVersion: String,
    val versionCode: Int,
    val deviceModel: String,
    val androidVersion: String,
    val locale: String,
    val syncStatus: String,
    val syncError: String?,
    val message: String?,
    val logs: String,
)

@JsonClass(generateAdapter = true)
data class ClientLogReceiptDto(
    val id: String,
    val createdAt: String,
)
