package com.florapin.app.network.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stockage des jetons dans des EncryptedSharedPreferences (chiffrées au repos).
 *
 * Résilience (C4) : si le fichier de prefs est indéchiffrable (typiquement une
 * restauration de sauvegarde sur un autre appareil, où la clé Keystore d'origine
 * n'existe pas), on supprime le fichier corrompu et on repart d'un stockage
 * vierge au lieu de crasher au lancement — l'utilisateur devra simplement se
 * reconnecter.
 */
class EncryptedTokenStore(context: Context) : TokenStore {

    private val prefs: SharedPreferences = run {
        val appContext = context.applicationContext
        try {
            createEncryptedPrefs(appContext)
        } catch (_: Exception) {
            appContext.deleteSharedPreferences(PREFS_FILE)
            createEncryptedPrefs(appContext)
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun accessToken(): String? = prefs.getString(KEY_ACCESS, null)

    override fun refreshToken(): String? = prefs.getString(KEY_REFRESH, null)

    override fun save(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .apply()
    }

    override fun userId(): String? = prefs.getString(KEY_USER_ID, null)

    override fun saveUserId(userId: String) {
        prefs.edit().putString(KEY_USER_ID, userId).apply()
    }

    override fun displayName(): String? = prefs.getString(KEY_DISPLAY_NAME, null)

    override fun saveDisplayName(displayName: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        /** Nom du fichier de prefs — exclu des sauvegardes (voir res/xml). */
        const val PREFS_FILE = "florapin_auth"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
    }
}
