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
    /** Horodatage du « Merci 🌸 » du propriétaire (null si non remercié — TÂCHE 4.3). */
    val thankedAt: String? = null,
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

/**
 * Statut d'une demande d'identification collaborative (TÂCHE 4.2), visible des
 * deux côtés (propriétaire et ami). Dérivé sans colonne dédiée : une demande est
 * « résolue » dès que la fleur n'attend plus d'identification et qu'une
 * proposition a été acceptée (l'espèce est posée), « en attente » sinon.
 */
enum class IdentificationStatus { PENDING, RESOLVED }

/**
 * Statut d'une demande à partir de son état autoritaire : la fleur attend-elle
 * encore une identification, et une proposition a-t-elle été acceptée (NODE-133).
 */
fun identificationStatusOf(
    needsIdentification: Boolean,
    proposals: List<SpeciesProposalDto>,
): IdentificationStatus =
    if (!needsIdentification && proposals.any { it.status == "accepted" }) {
        IdentificationStatus.RESOLVED
    } else {
        IdentificationStatus.PENDING
    }

/** Statut de MA demande (côté propriétaire, TÂCHE 4.2). */
fun MyIdentificationRequestDto.identificationStatus(): IdentificationStatus =
    identificationStatusOf(flower.needsIdentification, proposals)

/**
 * Statut d'une fleur « à identifier » côté ami (TÂCHE 4.2) : l'ami n'a pas le
 * détail des propositions, mais une fleur qui n'attend plus d'identification a
 * été tranchée par son propriétaire (proposition acceptée ou demande levée).
 */
fun FlowerDto.identificationStatus(): IdentificationStatus =
    if (needsIdentification) IdentificationStatus.PENDING else IdentificationStatus.RESOLVED
