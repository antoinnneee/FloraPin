package com.florapin.app.network.api

import com.florapin.app.network.dto.FlowerDto
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

    /** Propose une espèce pour une fleur d'un ami. */
    @POST("flowers/{id}/proposals")
    suspend fun propose(
        @Path("id") flowerId: String,
        @Body body: ProposeSpeciesRequest,
    ): SpeciesProposalDto
}
