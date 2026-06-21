package com.florapin.app.network.api

import com.florapin.app.network.dto.RegisterDeviceRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

interface PushApi {
    @POST("push/devices")
    suspend fun register(@Body body: RegisterDeviceRequest): Response<Unit>

    @DELETE("push/devices/{token}")
    suspend fun unregister(@Path("token") token: String): Response<Unit>
}
