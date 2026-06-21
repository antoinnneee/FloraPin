package com.florapin.app.network.api

import com.florapin.app.network.dto.CreateFlowerRequest
import com.florapin.app.network.dto.CreateFlowerResponse
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.ImageUrlResponse
import com.florapin.app.network.dto.UpdateFlowerRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FlowersApi {
    @POST("flowers")
    suspend fun create(@Body body: CreateFlowerRequest): CreateFlowerResponse

    @GET("flowers")
    suspend fun list(
        @Query("species") species: String? = null,
        @Query("tag") tag: String? = null,
    ): List<FlowerDto>

    @GET("flowers/{id}")
    suspend fun get(@Path("id") id: String): FlowerDto

    @GET("flowers/{id}/image-url")
    suspend fun imageUrl(@Path("id") id: String): ImageUrlResponse

    @PATCH("flowers/{id}")
    suspend fun update(
        @Path("id") id: String,
        @Body body: UpdateFlowerRequest,
    ): FlowerDto

    @DELETE("flowers/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}
