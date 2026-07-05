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
    /** Nom d'affichage de l'ami qui a proposé (peut être vide). */
    val proposedByName: String = "",
    val species: String,
    val status: String,
    val createdAt: String,
)

/** Statistiques collaboratives de l'utilisateur (page de profil). */
@JsonClass(generateAdapter = true)
data class ProposalStatsDto(
    /** Nombre de mes propositions d'espèce acceptées par des amis. */
    val acceptedProposals: Int,
)

/**
 * Une de mes demandes d'identification (TÂCHE 4.1) : ma fleur en attente et les
 * propositions d'espèce reçues de mes amis (« qui a proposé quoi »).
 */
@JsonClass(generateAdapter = true)
data class MyIdentificationRequestDto(
    val flower: FlowerDto,
    val proposals: List<SpeciesProposalDto> = emptyList(),
)
