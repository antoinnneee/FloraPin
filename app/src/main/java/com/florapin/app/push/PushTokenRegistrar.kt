package com.florapin.app.push

import android.content.Context
import android.util.Log
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.RegisterDeviceRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Enregistre / désenregistre le jeton FCM de l'appareil auprès du backend
 * (`/push/devices`). Les appels sont best-effort : un échec est journalisé sans
 * impacter l'utilisateur (le serveur purge de toute façon les jetons morts).
 */
object PushTokenRegistrar {

    private const val TAG = "PushTokenRegistrar"

    /** Récupère le jeton FCM et l'enregistre (no-op si non connecté). */
    fun register(context: Context) {
        val appContext = context.applicationContext
        val store = EncryptedTokenStore(appContext)
        if (store.refreshToken() == null) return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        NetworkModule.createAuthenticated(store).push
                            .register(RegisterDeviceRequest(token, "android"))
                    }.onFailure { Log.w(TAG, "register échec", it) }
                }
            }
            .addOnFailureListener { Log.w(TAG, "jeton FCM indisponible", it) }
    }

    /** Désenregistre le jeton courant (à la déconnexion), best-effort. */
    fun unregister(context: Context) {
        val appContext = context.applicationContext
        val store = EncryptedTokenStore(appContext)
        if (store.refreshToken() == null) return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching {
                        NetworkModule.createAuthenticated(store).push
                            .unregister(token)
                    }.onFailure { Log.w(TAG, "unregister échec", it) }
                }
            }
    }
}
