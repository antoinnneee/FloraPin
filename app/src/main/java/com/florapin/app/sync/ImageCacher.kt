package com.florapin.app.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.concurrent.TimeUnit
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

    companion object {
        /**
         * Télécharge [url] et la décode en [Bitmap], sans jamais lever d'exception
         * (renvoie null en cas d'échec réseau, de dépassement de délai ou de
         * décodage impossible).
         *
         * Pensé pour la miniature d'une notification push (BigPictureStyle) :
         * `onMessageReceived` dispose d'un budget d'environ 10 s → on borne le
         * téléchargement avec des timeouts COURTS ([timeoutMs], call timeout
         * global inclus) et on retombe proprement sur une notification sans image.
         *
         * SYNCHRONE : `onMessageReceived` s'exécute déjà hors du thread principal,
         * donc bloquer ici est acceptable ; le call timeout garantit qu'on ne
         * dépasse jamais le budget imparti.
         *
         * Client OkHttp dédié (non partagé, sans en-tête d'auth) : les URLs
         * présignées de lecture sont publiques, à l'image de [ImageCacher.cache].
         */
        fun downloadBitmap(url: String, timeoutMs: Long = 2_500L): Bitmap? {
            val client = OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                // Borne dure du temps total passé ici, quoi qu'il arrive.
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            return try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val bytes = response.body?.bytes() ?: return null
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}
