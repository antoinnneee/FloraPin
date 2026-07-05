package com.florapin.app.network.dto

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dérivation du statut d'une demande d'identification (TÂCHE 4.2) : « résolue »
 * seulement quand la fleur n'attend plus d'identification ET qu'une proposition a
 * été acceptée. Aucune colonne dédiée : tout se déduit de l'état existant.
 */
class IdentificationStatusTest {

    private fun proposal(status: String) = SpeciesProposalDto(
        id = "p",
        flowerId = "f",
        proposedBy = "friend",
        species = "Coquelicot",
        status = status,
        createdAt = "t",
    )

    private fun flower(needsIdentification: Boolean) = FlowerDto(
        id = "f",
        ownerId = "me",
        imageUrl = "https://x/f.jpg",
        takenAt = "t",
        notes = "",
        visibility = "private",
        needsIdentification = needsIdentification,
        createdAt = "t",
        updatedAt = "t",
    )

    @Test
    fun pending_whenFlowerStillNeedsIdentification() {
        assertEquals(
            IdentificationStatus.PENDING,
            identificationStatusOf(needsIdentification = true, proposals = emptyList()),
        )
        // Une proposition acceptée mais la fleur encore ouverte reste « en attente ».
        assertEquals(
            IdentificationStatus.PENDING,
            identificationStatusOf(
                needsIdentification = true,
                proposals = listOf(proposal("accepted")),
            ),
        )
    }

    @Test
    fun pending_whenClosedButNoAcceptedProposal() {
        // Demande levée sans acceptation (annulation) : pas « résolue ».
        assertEquals(
            IdentificationStatus.PENDING,
            identificationStatusOf(
                needsIdentification = false,
                proposals = listOf(proposal("pending")),
            ),
        )
    }

    @Test
    fun resolved_whenClosedWithAcceptedProposal() {
        assertEquals(
            IdentificationStatus.RESOLVED,
            identificationStatusOf(
                needsIdentification = false,
                proposals = listOf(proposal("pending"), proposal("accepted")),
            ),
        )
    }

    @Test
    fun myRequest_derivesFromFlowerAndProposals() {
        val open = MyIdentificationRequestDto(
            flower = flower(needsIdentification = true),
            proposals = listOf(proposal("pending")),
        )
        assertEquals(IdentificationStatus.PENDING, open.identificationStatus())

        val solved = MyIdentificationRequestDto(
            flower = flower(needsIdentification = false),
            proposals = listOf(proposal("accepted")),
        )
        assertEquals(IdentificationStatus.RESOLVED, solved.identificationStatus())
    }

    @Test
    fun flower_friendSide_derivesFromNeedsIdentification() {
        assertEquals(
            IdentificationStatus.PENDING,
            flower(needsIdentification = true).identificationStatus(),
        )
        assertEquals(
            IdentificationStatus.RESOLVED,
            flower(needsIdentification = false).identificationStatus(),
        )
    }
}
