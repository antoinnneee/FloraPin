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

/** Corps de POST /auth/email/verify (NODE-117). */
@JsonClass(generateAdapter = true)
data class VerifyEmailRequest(
    val token: String,
)

/** Corps de PATCH /users/me/email (NODE-117). */
@JsonClass(generateAdapter = true)
data class ChangeEmailRequest(
    val email: String,
)

/** Corps de PATCH /users/me (TÂCHE 1.7) : modification du nom d'affichage. */
@JsonClass(generateAdapter = true)
data class UpdateProfileRequest(
    val displayName: String,
)

/** Corps de POST /auth/change-password (vérification de l'ancien mot de passe). */
@JsonClass(generateAdapter = true)
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String,
)

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: String,
    val emailVerified: Boolean = false,
    /** URL présignée de l'avatar (TÂCHE 5.1), ou null si l'utilisateur n'en a pas. */
    val avatarUrl: String? = null,
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
