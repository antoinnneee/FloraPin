package com.florapin.app.network.api

import com.florapin.app.network.dto.SyncPullResponse
import com.florapin.app.network.dto.SyncPushItemResult
import com.florapin.app.network.dto.SyncPushRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SyncApi {
    @GET("sync")
    suspend fun pull(@Query("since") since: String? = null): SyncPullResponse

    @POST("sync/flowers")
    suspend fun push(@Body body: SyncPushRequest): List<SyncPushItemResult>
}
