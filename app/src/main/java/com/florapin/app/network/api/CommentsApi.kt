package com.florapin.app.network.api

import com.florapin.app.network.dto.CreateCommentRequest
import com.florapin.app.network.dto.FlowerCommentDto
import com.florapin.app.network.dto.UpdateCommentRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Fil de discussion sur une fleur : toute personne qui voit la fleur
 * (propriétaire, partage ciblé ou diffusion au réseau) peut commenter.
 */
interface CommentsApi {
    /** Poste un commentaire sur une fleur visible. */
    @POST("flowers/{id}/comments")
    suspend fun post(
        @Path("id") flowerId: String,
        @Body body: CreateCommentRequest,
    ): FlowerCommentDto

    /** Liste les commentaires d'une fleur (du plus ancien au plus récent). */
    @GET("flowers/{id}/comments")
    suspend fun list(@Path("id") flowerId: String): List<FlowerCommentDto>

    /** Édite un commentaire (réservé à son auteur). */
    @PATCH("flowers/{id}/comments/{commentId}")
    suspend fun update(
        @Path("id") flowerId: String,
        @Path("commentId") commentId: String,
        @Body body: UpdateCommentRequest,
    ): FlowerCommentDto

    /** Supprime un commentaire (auteur ou propriétaire de la fleur). */
    @DELETE("flowers/{id}/comments/{commentId}")
    suspend fun delete(
        @Path("id") flowerId: String,
        @Path("commentId") commentId: String,
    ): Response<Unit>
}
