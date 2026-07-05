package com.florapin.app.network.api

import com.florapin.app.network.dto.LikerDto
import com.florapin.app.network.dto.ReactionRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Réactions sur les fleurs (NODE-140 / TÂCHE 3.5) : pose/retrait et liste des likers. */
interface LikesApi {
    /** Pose la réaction par défaut (cœur), corps absent — compat ascendante. */
    @POST("flowers/{id}/like")
    suspend fun like(@Path("id") flowerId: String): Response<Unit>

    /** Pose (ou change) une réaction typée : le corps porte le code de réaction. */
    @POST("flowers/{id}/like")
    suspend fun react(
        @Path("id") flowerId: String,
        @Body body: ReactionRequest,
    ): Response<Unit>

    @DELETE("flowers/{id}/like")
    suspend fun unlike(@Path("id") flowerId: String): Response<Unit>

    /** Liste les utilisateurs ayant liké la fleur (du plus ancien au plus récent). */
    @GET("flowers/{id}/likes")
    suspend fun likers(@Path("id") flowerId: String): List<LikerDto>
}
