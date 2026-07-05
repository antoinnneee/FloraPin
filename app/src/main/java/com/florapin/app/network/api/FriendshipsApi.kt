package com.florapin.app.network.api

import com.florapin.app.network.dto.AddFriendByIdRequest
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.FriendProfileDto
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

    /** Ajout d'ami par QR code (TÂCHE 4.5) : envoie l'id (UUID) scanné. */
    @POST("friendships/by-id")
    suspend fun requestById(@Body body: AddFriendByIdRequest): FriendshipDto

    @POST("friendships/{id}/accept")
    suspend fun accept(@Path("id") id: String): FriendshipDto

    @DELETE("friendships/{id}")
    suspend fun remove(@Path("id") id: String): Response<Unit>

    /** Profil public limité d'un ami (TÂCHE 5.7) : `id` = UUID de l'ami. */
    @GET("users/{id}/profile")
    suspend fun profile(@Path("id") id: String): FriendProfileDto
}
