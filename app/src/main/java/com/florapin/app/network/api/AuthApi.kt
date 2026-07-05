package com.florapin.app.network.api

import com.florapin.app.network.dto.AuthResponse
import com.florapin.app.network.dto.DeleteAccountRequest
import com.florapin.app.network.dto.ForgotPasswordRequest
import com.florapin.app.network.dto.LoginRequest
import com.florapin.app.network.dto.ChangeEmailRequest
import com.florapin.app.network.dto.ChangePasswordRequest
import com.florapin.app.network.dto.RefreshRequest
import com.florapin.app.network.dto.RegisterRequest
import com.florapin.app.network.dto.ResetPasswordRequest
import com.florapin.app.network.dto.TokenPair
import com.florapin.app.network.dto.UpdateProfileRequest
import com.florapin.app.network.dto.UserDto
import com.florapin.app.network.dto.VerifyEmailRequest
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenPair

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest): Response<Unit>

    /** Démarre un « mot de passe oublié » : envoie un lien si le compte existe. */
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<Unit>

    /** Termine la réinitialisation avec le token reçu par email. */
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<Unit>

    /**
     * Change le mot de passe (JWT requis, vérification de l'ancien). Renvoie une
     * nouvelle paire de jetons pour l'appareil courant ; les autres sessions sont
     * révoquées côté serveur.
     */
    @POST("auth/change-password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): TokenPair

    /** Demande/renvoie l'email de vérification d'adresse (JWT requis). */
    @POST("auth/email/verification")
    suspend fun requestEmailVerification(): Response<Unit>

    /** Valide l'adresse via le token reçu par email. */
    @POST("auth/email/verify")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest): Response<Unit>

    /** Change l'adresse email (autorisé tant qu'elle n'est pas vérifiée). */
    @PATCH("users/me/email")
    suspend fun changeEmail(@Body body: ChangeEmailRequest): UserDto

    /** Modifie le nom d'affichage (TÂCHE 1.7). */
    @PATCH("users/me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest): UserDto

    /**
     * Téléverse (ou remplace) l'avatar (TÂCHE 5.1, multipart, champ `file`) ; le
     * serveur réencode en WebP et renvoie le profil à jour (avec `avatarUrl`).
     */
    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(@Part file: MultipartBody.Part): UserDto

    /**
     * Profil de l'utilisateur courant (session restaurée sans relogin).
     * Mappé sur le endpoint existant `GET /users/me` (route protégée par JWT).
     */
    @GET("users/me")
    suspend fun me(): UserDto

    /**
     * Supprime définitivement le compte courant et toutes ses données (NODE-118).
     * DELETE avec corps (mot de passe) : `@HTTP(hasBody = true)` car `@DELETE`
     * ne permet pas de `@Body`.
     */
    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteAccount(@Body body: DeleteAccountRequest): Response<Unit>
}
