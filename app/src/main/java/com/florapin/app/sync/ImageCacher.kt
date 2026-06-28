package com.florapin.app.sync

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Met en cache localement les images de fleurs/photos tirées du serveur.
 *
 * Pourquoi : l'app est device-first et PERSISTE en base les URLs présignées
 * (MinIO/S3) reçues à la synchro. Ces URLs expirent (SigV4) ; une fois périmées,
 * Coil ne peut plus les charger → toutes les vignettes deviennent vides. En
 * téléchargeant le fichier dès le pull et en renseignant `imagePath`, l'affichage
 * ne dépend plus jamais de l'expiration des URLs.
 *
 * Le nommage du fichier est STABLE (dérivé de l'id serveur) : un re-téléchargement
 * est idempotent et n'accumule pas de doublons.
 */
class ImageCacher(
    private val client: OkHttpClient,
    /** Répertoire de stockage privé des images (cf. PhotoStorage.photosDir). */
    private val targetDir: File,
) {
    /**
     * Télécharge [url] vers un fichier local nommé d'après [key] (id serveur).
     * Retourne le chemin absolu du fichier, ou null si le téléchargement échoue
     * (URL expirée, réseau, etc.) — l'appelant conserve alors l'URL distante en
     * repli, sans planter la synchro.
     */
    suspend fun cache(key: String, url: String): String? = withContext(Dispatchers.IO) {
        val target = File(targetDir.apply { mkdirs() }, "REMOTE_$key.img")
        // Déjà en cache (fichier non vide) : rien à retélécharger.
        if (target.exists() && target.length() > 0L) return@withContext target.absolutePath
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (target.length() > 0L) target.absolutePath else null
        } catch (_: Exception) {
            runCatching { if (target.exists()) target.delete() }
            null
        }
    }
}
