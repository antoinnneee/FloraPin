package com.florapin.app.network.auth

import com.florapin.app.network.api.AuthApi
import com.florapin.app.network.dto.RefreshRequest
import com.florapin.app.network.dto.TokenPair
import java.io.IOException
import kotlinx.coroutines.runBlocking
import retrofit2.HttpException

/** Issue d'une tentative de rafraîchissement des jetons (I12). */
sealed interface RefreshResult {
    /** Nouvelle paire de jetons obtenue. */
    data class Success(val tokens: TokenPair) : RefreshResult

    /** Le serveur a refusé le refresh (401/403) : la session est invalide. */
    data object Rejected : RefreshResult

    /**
     * Échec transitoire (réseau coupé, 5xx…) : la session est peut-être encore
     * valide, il ne faut PAS déconnecter l'utilisateur.
     */
    data object TransientError : RefreshResult
}

/** Rafraîchit la paire de jetons à partir d'un refresh token. */
interface TokenRefresher {
    /** Tente le refresh et qualifie l'échec éventuel (réseau vs refus serveur). */
    fun refresh(refreshToken: String): RefreshResult
}

/**
 * Implémentation appuyée sur un [AuthApi] **sans** authenticator (pour éviter
 * toute récursion). L'appel suspend est exécuté de façon bloquante car invoqué
 * depuis l'Authenticator OkHttp (thread d'arrière-plan).
 */
class RetrofitTokenRefresher(private val authApi: AuthApi) : TokenRefresher {
    override fun refresh(refreshToken: String): RefreshResult = try {
        RefreshResult.Success(
            runBlocking { authApi.refresh(RefreshRequest(refreshToken)) },
        )
    } catch (e: HttpException) {
        // Seul un refus explicite du serveur invalide la session ; un 5xx est
        // traité comme transitoire.
        if (e.code() == 401 || e.code() == 403) {
            RefreshResult.Rejected
        } else {
            RefreshResult.TransientError
        }
    } catch (e: IOException) {
        // Pas de réseau / timeout : on ne sait rien de la validité du token.
        RefreshResult.TransientError
    } catch (e: Exception) {
        // Erreur inattendue : prudence, ne pas déconnecter l'utilisateur.
        RefreshResult.TransientError
    }
}
