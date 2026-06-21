package com.florapin.app.network.auth

import com.florapin.app.network.api.AuthApi
import com.florapin.app.network.dto.RefreshRequest
import com.florapin.app.network.dto.TokenPair
import kotlinx.coroutines.runBlocking

/** Rafraîchit la paire de jetons à partir d'un refresh token. */
interface TokenRefresher {
    /** Renvoie une nouvelle paire, ou null si le refresh échoue. */
    fun refresh(refreshToken: String): TokenPair?
}

/**
 * Implémentation appuyée sur un [AuthApi] **sans** authenticator (pour éviter
 * toute récursion). L'appel suspend est exécuté de façon bloquante car invoqué
 * depuis l'Authenticator OkHttp (thread d'arrière-plan).
 */
class RetrofitTokenRefresher(private val authApi: AuthApi) : TokenRefresher {
    override fun refresh(refreshToken: String): TokenPair? =
        runCatching { runBlocking { authApi.refresh(RefreshRequest(refreshToken)) } }
            .getOrNull()
}
