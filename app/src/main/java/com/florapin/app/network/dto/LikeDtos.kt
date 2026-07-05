package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs des cœurs sur une fleur. */

/** Un « liker » renvoyé par GET flowers/{id}/likes : identifiant + nom. */
@JsonClass(generateAdapter = true)
data class LikerDto(
    val userId: String,
    /** Nom d'affichage du liker (peut être vide). */
    val displayName: String = "",
)
