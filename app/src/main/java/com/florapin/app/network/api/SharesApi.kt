package com.florapin.app.network.api

import com.florapin.app.network.dto.CreateShareRequest
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.ShareDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface SharesApi {
    @POST("shares")
    suspend fun create(@Body body: CreateShareRequest): ShareDto

    @GET("shares")
    suspend fun listMine(): List<ShareDto>

    @DELETE("shares/{id}")
    suspend fun revoke(@Path("id") id: String): Response<Unit>

    @GET("shared")
    suspend fun sharedWithMe(): List<FlowerDto>
}
