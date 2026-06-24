package com.florapin.app.network.api

import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

/** Cœurs sur les fleurs (NODE-140) : pose/retrait idempotents. */
interface LikesApi {
    @POST("flowers/{id}/like")
    suspend fun like(@Path("id") flowerId: String): Response<Unit>

    @DELETE("flowers/{id}/like")
    suspend fun unlike(@Path("id") flowerId: String): Response<Unit>
}
