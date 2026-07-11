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

    /** Profil minimal utilisable après validation locale du dernier mot de passe. */
    fun saveOfflineLogin(email: String, password: String, profile: OfflineLoginProfile) {}

    /** Renvoie le profil seulement si l'email et le dernier mot de passe correspondent. */
    fun offlineLogin(email: String, password: String): OfflineLoginProfile? = null

    /** Remplace le vérificateur local après un changement de mot de passe réussi. */
    fun updateOfflinePassword(password: String) {}

    /** Efface aussi l'accès hors-ligne, notamment après suppression du compte. */
    fun clearOfflineLogin() {}
}

data class OfflineLoginProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val createdAt: String,
    val emailVerified: Boolean,
    val avatarUrl: String?,
)
