package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/**
 * DTOs d'authentification (alignés sur backend/docs/API.md).
 * Dates en ISO 8601 UTC (String), identifiants UUID (String).
 */
@JsonClass(generateAdapter = true)
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String,
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    val refreshToken: String,
)

/** Corps de DELETE /users/me : re-authentification (effacement RGPD, NODE-118). */
@JsonClass(generateAdapter = true)
data class DeleteAccountRequest(
    val password: String,
)

/** Corps de POST /auth/forgot-password (NODE-116). */
@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(
    val email: String,
)

/** Corps de POST /auth/reset-password (NODE-116). */
@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    val user: UserDto,
    val accessToken: String,
    val refreshToken: String,
)

@JsonClass(generateAdapter = true)
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)
