package com.florapin.app.network.api

import com.florapin.app.network.dto.CreateGroupRequest
import com.florapin.app.network.dto.GroupDto
import com.florapin.app.network.dto.InviteMemberRequest
import com.florapin.app.network.dto.UpdateGroupRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Groupes collaboratifs (TÂCHE 7.1). API réseau uniquement (device-first : la
 * gestion des groupes suppose une connexion ; hors-ligne, l'écran dégrade).
 */
interface GroupsApi {
    @GET("groups")
    suspend fun list(): List<GroupDto>

    @GET("groups/{id}")
    suspend fun get(@Path("id") id: String): GroupDto

    @POST("groups")
    suspend fun create(@Body body: CreateGroupRequest): GroupDto

    @PATCH("groups/{id}")
    suspend fun rename(@Path("id") id: String, @Body body: UpdateGroupRequest): GroupDto

    @DELETE("groups/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>

    /** Invite un ami dans le groupe (par UUID). */
    @POST("groups/{id}/members")
    suspend fun invite(
        @Path("id") id: String,
        @Body body: InviteMemberRequest,
    ): GroupDto

    /** Accepte l'invitation reçue. */
    @POST("groups/{id}/accept")
    suspend fun accept(@Path("id") id: String): GroupDto

    /** Retire un membre (propriétaire) ou quitte le groupe (soi-même). */
    @DELETE("groups/{id}/members/{userId}")
    suspend fun removeMember(
        @Path("id") id: String,
        @Path("userId") userId: String,
    ): Response<Unit>
}
