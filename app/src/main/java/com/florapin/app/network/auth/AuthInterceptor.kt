package com.florapin.app.network.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ajoute l'en-tête `Authorization: Bearer <access>` aux requêtes, sauf pour les
 * endpoints d'authentification (login/register/refresh/logout).
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.contains("/auth/")) {
            return chain.proceed(request)
        }
        val token = tokenStore.accessToken() ?: return chain.proceed(request)
        val authorized = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authorized)
    }
}
