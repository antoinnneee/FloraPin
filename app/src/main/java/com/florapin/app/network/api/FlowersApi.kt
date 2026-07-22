package com.florapin.app.network.api

import com.florapin.app.network.dto.CreateFlowerRequest
import com.florapin.app.network.dto.CreateFlowerResponse
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.ImageUrlResponse
import com.florapin.app.network.dto.UpdateFlowerRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
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

    /**
     * Téléverse les deux WebP déjà optimisés (`file` + `thumbnail`) ; le serveur
     * les valide sans les réencoder et renvoie la fleur à jour.
     */
    @Multipart
    @POST("flowers/{id}/image-variants")
    suspend fun uploadImage(
        @Path("id") id: String,
        @Part file: MultipartBody.Part,
        @Part thumbnail: MultipartBody.Part,
    ): FlowerDto

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
