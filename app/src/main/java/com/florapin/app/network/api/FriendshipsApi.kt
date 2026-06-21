package com.florapin.app.network.api

import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.FriendshipDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface FriendshipsApi {
    @GET("friendships")
    suspend fun list(): List<FriendshipDto>

    @POST("friendships")
    suspend fun request(@Body body: CreateFriendshipRequest): FriendshipDto

    @POST("friendships/{id}/accept")
    suspend fun accept(@Path("id") id: String): FriendshipDto

    @DELETE("friendships/{id}")
    suspend fun remove(@Path("id") id: String): Response<Unit>
}
