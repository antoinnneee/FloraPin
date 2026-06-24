package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs de l'identification collaborative (NODE-134). */

/** Corps de POST flowers/{id}/proposals : une proposition d'espèce. */
@JsonClass(generateAdapter = true)
data class ProposeSpeciesRequest(
    val species: String,
)

/** Proposition d'espèce renvoyée par l'API (NODE-31/134). */
@JsonClass(generateAdapter = true)
data class SpeciesProposalDto(
    val id: String,
    val flowerId: String,
    val proposedBy: String,
    val species: String,
    val status: String,
    val createdAt: String,
)
