package com.florapin.app.network.auth

/**
 * Purge des données locales liées à une session (fleurs, fichiers images,
 * curseur de sync). Invoqué à la déconnexion pour éviter qu'un compte hérite
 * des données d'un autre au login suivant (NODE-93).
 */
fun interface SessionDataCleaner {
    suspend fun clearLocalData()
}
