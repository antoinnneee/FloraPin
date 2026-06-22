package com.florapin.app.network.auth

import com.florapin.app.network.api.AuthApi
import com.florapin.app.network.dto.LoginRequest
import com.florapin.app.network.dto.RefreshRequest
import com.florapin.app.network.dto.RegisterRequest
import com.florapin.app.network.dto.UserDto

/** Orchestration de la session : connexion, inscription, déconnexion. */
class SessionManager(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
) {
    fun isLoggedIn(): Boolean = tokenStore.refreshToken() != null

    suspend fun login(email: String, password: String): UserDto {
        val res = authApi.login(LoginRequest(email, password))
        tokenStore.save(res.accessToken, res.refreshToken)
        tokenStore.saveUserId(res.user.id)
        tokenStore.saveDisplayName(res.user.displayName)
        return res.user
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
        return res.user
    }

    /** Révoque le refresh côté serveur (best-effort) puis purge le stockage. */
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
}
