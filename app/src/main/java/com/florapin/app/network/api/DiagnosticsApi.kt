package com.florapin.app.network.api

import com.florapin.app.network.dto.ClientLogReceiptDto
import com.florapin.app.network.dto.CreateClientLogRequest
import retrofit2.http.Body
import retrofit2.http.POST

interface DiagnosticsApi {
    @POST("diagnostics/logs")
    suspend fun sendLogs(@Body body: CreateClientLogRequest): ClientLogReceiptDto
}
