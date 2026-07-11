package com.florapin.app.network.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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
        // La déconnexion termine la session réseau mais conserve le vérificateur
        // du dernier compte pour permettre une reconnexion locale hors-ligne.
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_USER_ID)
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }

    override fun saveOfflineLogin(
        email: String,
        password: String,
        profile: OfflineLoginProfile,
    ) {
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val verifier = derivePassword(password, salt)
        prefs.edit()
            .putString(KEY_OFFLINE_EMAIL, email.normalizedEmail())
            .putString(KEY_OFFLINE_USER_ID, profile.id)
            .putString(KEY_OFFLINE_DISPLAY_NAME, profile.displayName)
            .putString(KEY_OFFLINE_CREATED_AT, profile.createdAt)
            .putBoolean(KEY_OFFLINE_EMAIL_VERIFIED, profile.emailVerified)
            .putString(KEY_OFFLINE_AVATAR_URL, profile.avatarUrl)
            .putString(KEY_OFFLINE_SALT, salt.base64())
            .putString(KEY_OFFLINE_VERIFIER, verifier.base64())
            .apply()
    }

    override fun offlineLogin(email: String, password: String): OfflineLoginProfile? =
        runCatching {
            val cachedEmail = prefs.getString(KEY_OFFLINE_EMAIL, null) ?: return null
            if (cachedEmail != email.normalizedEmail()) return null
            val salt = prefs.getString(KEY_OFFLINE_SALT, null)?.decodeBase64() ?: return null
            val expected = prefs.getString(KEY_OFFLINE_VERIFIER, null)?.decodeBase64() ?: return null
            val actual = derivePassword(password, salt)
            if (!MessageDigest.isEqual(expected, actual)) return null
            OfflineLoginProfile(
                id = prefs.getString(KEY_OFFLINE_USER_ID, null) ?: return null,
                email = cachedEmail,
                displayName = prefs.getString(KEY_OFFLINE_DISPLAY_NAME, null) ?: return null,
                createdAt = prefs.getString(KEY_OFFLINE_CREATED_AT, "").orEmpty(),
                emailVerified = prefs.getBoolean(KEY_OFFLINE_EMAIL_VERIFIED, false),
                avatarUrl = prefs.getString(KEY_OFFLINE_AVATAR_URL, null),
            )
        }.getOrNull()

    override fun updateOfflinePassword(password: String) {
        if (!prefs.contains(KEY_OFFLINE_EMAIL)) return
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        prefs.edit()
            .putString(KEY_OFFLINE_SALT, salt.base64())
            .putString(KEY_OFFLINE_VERIFIER, derivePassword(password, salt).base64())
            .apply()
    }

    override fun clearOfflineLogin() {
        prefs.edit()
            .remove(KEY_OFFLINE_EMAIL)
            .remove(KEY_OFFLINE_USER_ID)
            .remove(KEY_OFFLINE_DISPLAY_NAME)
            .remove(KEY_OFFLINE_CREATED_AT)
            .remove(KEY_OFFLINE_EMAIL_VERIFIED)
            .remove(KEY_OFFLINE_AVATAR_URL)
            .remove(KEY_OFFLINE_SALT)
            .remove(KEY_OFFLINE_VERIFIER)
            .apply()
    }

    private fun derivePassword(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
    private fun String.normalizedEmail(): String = trim().lowercase()

    private companion object {
        /** Nom du fichier de prefs — exclu des sauvegardes (voir res/xml). */
        const val PREFS_FILE = "florapin_auth"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_OFFLINE_EMAIL = "offline_email"
        const val KEY_OFFLINE_USER_ID = "offline_user_id"
        const val KEY_OFFLINE_DISPLAY_NAME = "offline_display_name"
        const val KEY_OFFLINE_CREATED_AT = "offline_created_at"
        const val KEY_OFFLINE_EMAIL_VERIFIED = "offline_email_verified"
        const val KEY_OFFLINE_AVATAR_URL = "offline_avatar_url"
        const val KEY_OFFLINE_SALT = "offline_password_salt"
        const val KEY_OFFLINE_VERIFIER = "offline_password_verifier"
        const val SALT_BYTES = 16
        const val PBKDF2_ITERATIONS = 120_000
        const val HASH_BITS = 256
    }
}
