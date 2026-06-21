package com.florapin.app.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

/**
 * Observe la connectivité réseau et déclenche une action au retour du réseau
 * (utile pour relancer la synchronisation des éléments en file d'attente).
 */
class ConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private var callback: ConnectivityManager.NetworkCallback? = null

    /** Vrai si une connexion Internet validée est disponible. */
    fun isOnline(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Enregistre un rappel exécuté à chaque réseau disponible. */
    fun start(onAvailable: () -> Unit) {
        if (callback != null) return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                onAvailable()
            }
        }
        callback = cb
        connectivityManager.registerNetworkCallback(request, cb)
    }

    fun stop() {
        callback?.let { connectivityManager.unregisterNetworkCallback(it) }
        callback = null
    }
}
