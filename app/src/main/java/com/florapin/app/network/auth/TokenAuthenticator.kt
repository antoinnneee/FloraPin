package com.florapin.app.network.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Sur un 401, tente un refresh des jetons (rotation) puis rejoue la requête avec
 * le nouvel access token. Abandonne (et purge) si le refresh échoue, et limite
 * le nombre de tentatives pour éviter les boucles.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val refresher: TokenRefresher,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null // déjà retenté

        val refreshToken = tokenStore.refreshToken() ?: return null
        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")

        synchronized(lock) {
            val current = tokenStore.accessToken()
            // Un autre thread a peut-être déjà rafraîchi : rejouer avec le récent.
            if (current != null && current != failedToken) {
                return retryWith(response, current)
            }

            val pair = refresher.refresh(refreshToken)
            if (pair == null) {
                tokenStore.clear()
                return null
            }
            tokenStore.save(pair.accessToken, pair.refreshToken)
            return retryWith(response, pair.accessToken)
        }
    }

    private fun retryWith(response: Response, accessToken: String): Request =
        response.request.newBuilder()
            .header("Authorization", "Bearer $accessToken")
            .build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
