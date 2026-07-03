package com.florapin.app.network.auth

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Ajoute l'en-tête `Authorization: Bearer <access>` aux requêtes, sauf pour les
 * endpoints d'authentification PUBLICS (login/register/refresh/logout/mot de
 * passe oublié/reset/verify).
 *
 * Attention : tous les endpoints sous `/auth/` ne sont pas publics —
 * `POST auth/email/verification` (renvoi de l'email de vérification) EXIGE un
 * JWT. On ne peut donc pas exclure `/auth/` en bloc (I13) : cet endpoint
 * échouait systématiquement en 401 dès que l'access token était présent mais
 * non transmis. On liste explicitement les chemins publics et on attache le
 * token partout ailleurs.
 */
class AuthInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        if (PUBLIC_AUTH_PATHS.any { path.endsWith(it) }) {
            return chain.proceed(request)
        }
        val token = tokenStore.accessToken() ?: return chain.proceed(request)
        val authorized = request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authorized)
    }

    private companion object {
        /**
         * Chemins `/auth/` qui NE portent PAS de JWT (l'access token n'existe pas
         * encore ou n'est pas requis). Tout le reste — dont
         * `auth/email/verification` — reçoit le token. `auth/email/verify`
         * (validation par token email) est public ; il ne finit pas par
         * « verification », les deux se distinguent donc par `endsWith`.
         */
        val PUBLIC_AUTH_PATHS = listOf(
            "auth/register",
            "auth/login",
            "auth/refresh",
            "auth/logout",
            "auth/forgot-password",
            "auth/reset-password",
            "auth/email/verify",
        )
    }
}
