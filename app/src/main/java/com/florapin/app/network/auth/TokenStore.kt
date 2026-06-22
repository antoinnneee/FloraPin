package com.florapin.app.network.auth

/** Stockage des jetons d'authentification. */
interface TokenStore {
    fun accessToken(): String?
    fun refreshToken(): String?
    fun save(accessToken: String, refreshToken: String)
    fun clear()

    /** Id de l'utilisateur connecté (pour distinguer ses fleurs de celles des amis). */
    fun userId(): String? = null

    /** Mémorise l'id de l'utilisateur connecté. */
    fun saveUserId(userId: String) {}

    /** Nom affiché de l'utilisateur connecté. */
    fun displayName(): String? = null

    /** Mémorise le nom affiché de l'utilisateur connecté. */
    fun saveDisplayName(displayName: String) {}
}
