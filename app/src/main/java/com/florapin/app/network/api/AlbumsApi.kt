package com.florapin.app.network.api

import com.florapin.app.network.dto.AlbumDto
import retrofit2.http.GET

interface AlbumsApi {
    @GET("albums")
    suspend fun list(): List<AlbumDto>
}
