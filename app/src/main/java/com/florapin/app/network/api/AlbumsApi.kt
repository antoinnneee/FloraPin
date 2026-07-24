package com.florapin.app.network.api

import com.florapin.app.network.dto.AddFlowerToAlbumRequest
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.CreateAlbumRequest
import com.florapin.app.network.dto.SetAlbumGroupRequest
import com.florapin.app.network.dto.SetAlbumCoverRequest
import com.florapin.app.network.dto.SetAlbumPermissionsRequest
import com.florapin.app.network.dto.UpdateAlbumRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface AlbumsApi {
    @GET("albums")
    suspend fun list(): List<AlbumDto>

    @GET("albums/{id}")
    suspend fun get(@Path("id") id: String): AlbumDto

    @POST("albums")
    suspend fun create(@Body body: CreateAlbumRequest): AlbumDto

    @PATCH("albums/{id}")
    suspend fun rename(@Path("id") id: String, @Body body: UpdateAlbumRequest): AlbumDto

    @DELETE("albums/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>

    @POST("albums/{id}/flowers")
    suspend fun addFlower(
        @Path("id") id: String,
        @Body body: AddFlowerToAlbumRequest,
    ): AlbumDto

    @DELETE("albums/{id}/flowers/{flowerId}")
    suspend fun removeFlower(
        @Path("id") id: String,
        @Path("flowerId") flowerId: String,
    ): AlbumDto

    @PATCH("albums/{id}/cover")
    suspend fun setCover(
        @Path("id") id: String,
        @Body body: SetAlbumCoverRequest,
    ): AlbumDto

    /** Rattache/détache l'album à un groupe collaboratif (TÂCHE 7.1). */
    @PATCH("albums/{id}/group")
    suspend fun setGroup(
        @Path("id") id: String,
        @Body body: SetAlbumGroupRequest,
    ): AlbumDto

    /** Règle le régime de droits d'un album de groupe (TÂCHE 7.1). */
    @PATCH("albums/{id}/permissions")
    suspend fun setPermissions(
        @Path("id") id: String,
        @Body body: SetAlbumPermissionsRequest,
    ): AlbumDto
}
