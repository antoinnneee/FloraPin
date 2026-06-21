package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** Enregistrement d'un jeton d'appareil pour le push (FCM). */
@JsonClass(generateAdapter = true)
data class RegisterDeviceRequest(
    val token: String,
    val platform: String = "android",
)
