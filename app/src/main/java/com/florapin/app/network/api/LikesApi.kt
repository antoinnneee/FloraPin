package com.florapin.app.network.api

import com.florapin.app.network.dto.LikerDto
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Cœurs sur les fleurs (NODE-140) : pose/retrait idempotents et liste des likers. */
interface LikesApi {
    @POST("flowers/{id}/like")
    suspend fun like(@Path("id") flowerId: String): Response<Unit>

    @DELETE("flowers/{id}/like")
    suspend fun unlike(@Path("id") flowerId: String): Response<Unit>

    /** Liste les utilisateurs ayant liké la fleur (du plus ancien au plus récent). */
    @GET("flowers/{id}/likes")
    suspend fun likers(@Path("id") flowerId: String): List<LikerDto>
}
