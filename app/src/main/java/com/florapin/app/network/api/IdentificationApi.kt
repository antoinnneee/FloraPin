package com.florapin.app.network.api

import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.MyIdentificationRequestDto
import com.florapin.app.network.dto.ProposalStatsDto
import com.florapin.app.network.dto.ProposeSpeciesRequest
import com.florapin.app.network.dto.SpeciesProposalDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Identification collaborative (NODE-134).
 *
 * Côté propriétaire : solliciter/annuler une demande d'identification sur une de
 * ses fleurs non identifiée. Côté ami : lister les fleurs « à identifier » qui me
 * sont partagées et y répondre par une proposition d'espèce.
 */
interface IdentificationApi {
    /** Demande à mes amis d'identifier ma fleur (la marque « à identifier »). */
    @POST("flowers/{id}/identification-requests")
    suspend fun request(@Path("id") flowerId: String): Response<Unit>

    /** Annule la demande d'identification sur ma fleur. */
    @DELETE("flowers/{id}/identification-requests")
    suspend fun cancel(@Path("id") flowerId: String): Response<Unit>

    /** Les fleurs « à identifier » qui me sont partagées (vue côté ami). */
    @GET("identification-requests")
    suspend fun listToIdentify(): List<FlowerDto>

    /** L'état de MES demandes : mes fleurs en attente + propositions reçues (TÂCHE 4.1). */
    @GET("me/identification-requests")
    suspend fun listMyRequests(): List<MyIdentificationRequestDto>

    /** Propose une espèce pour une fleur d'un ami. */
    @POST("flowers/{id}/proposals")
    suspend fun propose(
        @Path("id") flowerId: String,
        @Body body: ProposeSpeciesRequest,
    ): SpeciesProposalDto

    /** Propositions d'espèce reçues sur ma fleur (côté propriétaire). */
    @GET("flowers/{id}/proposals")
    suspend fun listProposals(@Path("id") flowerId: String): List<SpeciesProposalDto>

    /** Accepte une proposition : l'espèce est appliquée à ma fleur (propriétaire). */
    @POST("flowers/{id}/proposals/{proposalId}/accept")
    suspend fun acceptProposal(
        @Path("id") flowerId: String,
        @Path("proposalId") proposalId: String,
    ): SpeciesProposalDto

    /** Refuse une proposition : elle est retirée de ma fleur (propriétaire). */
    @DELETE("flowers/{id}/proposals/{proposalId}")
    suspend fun rejectProposal(
        @Path("id") flowerId: String,
        @Path("proposalId") proposalId: String,
    ): Response<Unit>

    /** Mes statistiques collaboratives (nombre de propositions acceptées). */
    @GET("me/proposal-stats")
    suspend fun proposalStats(): ProposalStatsDto
}
