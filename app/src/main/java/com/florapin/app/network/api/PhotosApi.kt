package com.florapin.app.network.api

import com.florapin.app.network.dto.AddPhotoResponse
import com.florapin.app.network.dto.PhotoDto
import com.florapin.app.network.dto.ReorderPhotosRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/** Endpoints des photos d'une fleur (NODE-106). */
interface PhotosApi {
    @POST("flowers/{id}/photos")
    suspend fun add(@Path("id") flowerId: String): AddPhotoResponse

    /** Téléverse les WebP finaux (`file` + `thumbnail`) sans réencodage. */
    @Multipart
    @POST("flowers/{id}/photos/{photoId}/image-variants")
    suspend fun uploadImage(
        @Path("id") flowerId: String,
        @Path("photoId") photoId: String,
        @Part file: MultipartBody.Part,
        @Part thumbnail: MultipartBody.Part,
    ): PhotoDto

    @DELETE("flowers/{id}/photos/{photoId}")
    suspend fun remove(
        @Path("id") flowerId: String,
        @Path("photoId") photoId: String,
    ): Response<Unit>

    @PATCH("flowers/{id}/photos/order")
    suspend fun reorder(
        @Path("id") flowerId: String,
        @Body body: ReorderPhotosRequest,
    ): List<PhotoDto>
}
