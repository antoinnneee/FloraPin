package com.florapin.app.network.auth

/** Stockage des jetons d'authentification. */
interface TokenStore {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun save(accessToken: String, refreshToken: String)
    fun clear()
}
