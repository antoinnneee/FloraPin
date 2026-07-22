package com.florapin.app.network.auth

import com.florapin.app.network.api.AuthApi
import com.florapin.app.network.dto.ChangeEmailRequest
import com.florapin.app.network.dto.ChangePasswordRequest
import com.florapin.app.network.dto.DeleteAccountRequest
import com.florapin.app.network.dto.ForgotPasswordRequest
import com.florapin.app.network.dto.LoginRequest
import com.florapin.app.network.dto.RefreshRequest
import com.florapin.app.network.dto.RegisterRequest
import com.florapin.app.network.dto.ResetPasswordRequest
import com.florapin.app.network.dto.UpdateProfileRequest
import com.florapin.app.network.dto.UserDto
import com.florapin.app.network.dto.VerifyEmailRequest
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException

/** Orchestration de la session : connexion, inscription, déconnexion. */
class SessionManager(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val localData: SessionDataCleaner? = null,
) {
    fun isLoggedIn(): Boolean = tokenStore.refreshToken() != null

    suspend fun login(email: String, password: String): UserDto {
        return try {
            val res = authApi.login(LoginRequest(email, password))
            persistOnlineLogin(email, password, res.user, res.accessToken, res.refreshToken)
            res.user
        } catch (error: IOException) {
            val cached = tokenStore.offlineLogin(email, password) ?: throw error
            UserDto(
                id = cached.id,
                email = cached.email,
                displayName = cached.displayName,
                createdAt = cached.createdAt,
                emailVerified = cached.emailVerified,
                avatarUrl = cached.avatarUrl,
            )
        }
    }

    suspend fun register(
        email: String,
        password: String,
        displayName: String,
    ): UserDto {
        val res = authApi.register(RegisterRequest(email, password, displayName))
        tokenStore.save(res.accessToken, res.refreshToken)
        tokenStore.saveUserId(res.user.id)
        tokenStore.saveDisplayName(res.user.displayName)
        tokenStore.saveOfflineLogin(email, password, res.user.toOfflineProfile())
        return res.user
    }

    /**
     * Récupère le profil courant depuis le serveur (GET /users/me) et rafraîchit
     * l'id + le nom affiché persistés. Utile pour les sessions restaurées dont le
     * displayName n'aurait pas été capté (anciennes sessions).
     */
    suspend fun fetchCurrentUser(): UserDto {
        val user = authApi.me()
        tokenStore.saveUserId(user.id)
        tokenStore.saveDisplayName(user.displayName)
        return user
    }

    /**
     * Déconnexion : révoque le refresh côté serveur (best-effort) puis efface les
     * jetons locaux. Les données de session (fleurs, photos, fichiers) sont
     * CONSERVÉES sur l'appareil : la synchronisation est optionnelle et les photos
     * de base restent disponibles hors-ligne après reconnexion.
     */
    suspend fun logout() {
        val refresh = tokenStore.refreshToken()
        try {
            if (refresh != null) authApi.logout(RefreshRequest(refresh))
        } catch (_: Exception) {
            // déconnexion locale garantie quoi qu'il arrive
        } finally {
            tokenStore.clear()
        }
    }

    /**
     * Supprime définitivement le compte courant côté serveur (DELETE /users/me,
     * re-authentification par [password]) puis purge le stockage local (tokens +
     * données de session). En cas d'échec serveur (ex. mot de passe incorrect),
     * lève [HttpException] et ne touche pas au stockage local.
     */
    suspend fun deleteAccount(password: String) {
        val response = authApi.deleteAccount(DeleteAccountRequest(password))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        tokenStore.clear()
        tokenStore.clearOfflineLogin()
        localData?.clearLocalData()
    }

    /**
     * Demande un email de réinitialisation (NODE-116). Le serveur répond 200
     * même si l'email est inconnu (anti-énumération) : succès = requête acceptée.
     */
    suspend fun forgotPassword(email: String) {
        val response = authApi.forgotPassword(ForgotPasswordRequest(email))
        if (!response.isSuccessful) throw HttpException(response)
    }

    /** Applique un nouveau mot de passe via le token reçu par email (NODE-116). */
    suspend fun resetPassword(token: String, newPassword: String) {
        val response = authApi.resetPassword(ResetPasswordRequest(token, newPassword))
        if (!response.isSuccessful) throw HttpException(response)
    }

    /**
     * Change le mot de passe (vérification de l'ancien côté serveur). En cas de
     * succès, le serveur révoque les autres sessions et réémet une paire de
     * jetons pour cet appareil : on la persiste pour rester connecté. Un ancien
     * mot de passe erroné lève [HttpException] (401).
     */
    suspend fun changePassword(oldPassword: String, newPassword: String) {
        val pair = authApi.changePassword(
            ChangePasswordRequest(oldPassword, newPassword),
        )
        tokenStore.save(pair.accessToken, pair.refreshToken)
        tokenStore.updateOfflinePassword(newPassword)
    }

    /** Demande/renvoie l'email de vérification d'adresse (NODE-117, JWT). */
    suspend fun requestEmailVerification() {
        val response = authApi.requestEmailVerification()
        if (!response.isSuccessful) throw HttpException(response)
    }

    /** Valide l'adresse via le token reçu par email (NODE-117). */
    suspend fun verifyEmail(token: String) {
        val response = authApi.verifyEmail(VerifyEmailRequest(token))
        if (!response.isSuccessful) throw HttpException(response)
    }

    /**
     * Change l'adresse email tant qu'elle n'est pas vérifiée (NODE-117) et
     * met à jour le profil persisté. Renvoie l'utilisateur mis à jour.
     */
    suspend fun changeEmail(email: String): UserDto {
        val user = authApi.changeEmail(ChangeEmailRequest(email))
        tokenStore.saveDisplayName(user.displayName)
        return user
    }

    /**
     * Modifie le nom d'affichage (TÂCHE 1.7) et met à jour le nom persisté
     * localement (affiché immédiatement au prochain lancement). Renvoie
     * l'utilisateur mis à jour.
     */
    suspend fun updateDisplayName(displayName: String): UserDto {
        val user = authApi.updateProfile(UpdateProfileRequest(displayName))
        tokenStore.saveDisplayName(user.displayName)
        return user
    }

    /**
     * Téléverse l'avatar (TÂCHE 5.1) : l'image transite par l'API (multipart)
     * qui la réencode en WebP. Renvoie le profil à jour (avec `avatarUrl`).
     */
    suspend fun uploadAvatar(file: File): UserDto {
        val mediaType = when (file.extension.lowercase()) {
            "webp" -> "image/webp"
            "png" -> "image/png"
            else -> "image/jpeg"
        }
        val body = file.asRequestBody(mediaType.toMediaType())
        val part = MultipartBody.Part.createFormData("file", file.name, body)
        return authApi.uploadAvatar(part)
    }

    private fun persistOnlineLogin(
        email: String,
        password: String,
        user: UserDto,
        accessToken: String,
        refreshToken: String,
    ) {
        tokenStore.save(accessToken, refreshToken)
        tokenStore.saveUserId(user.id)
        tokenStore.saveDisplayName(user.displayName)
        tokenStore.saveOfflineLogin(email, password, user.toOfflineProfile())
    }

    private fun UserDto.toOfflineProfile() = OfflineLoginProfile(
        id = id,
        email = email,
        displayName = displayName,
        createdAt = createdAt,
        emailVerified = emailVerified,
        avatarUrl = avatarUrl,
    )
}
