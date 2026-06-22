package com.florapin.app.network.api

import com.florapin.app.network.dto.AuthResponse
import com.florapin.app.network.dto.LoginRequest
import com.florapin.app.network.dto.RefreshRequest
import com.florapin.app.network.dto.RegisterRequest
import com.florapin.app.network.dto.TokenPair
import com.florapin.app.network.dto.UserDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenPair

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest): Response<Unit>

    /**
     * Profil de l'utilisateur courant (session restaurée sans relogin).
     * Mappé sur le endpoint existant `GET /users/me` (route protégée par JWT).
     */
    @GET("users/me")
    suspend fun me(): UserDto
}
