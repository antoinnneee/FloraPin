package com.florapin.app.capture

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stockage des photos dans le répertoire privé de l'application
 * (`filesDir/photos`). Aucune permission de stockage n'est requise, et les
 * fichiers sont supprimés à la désinstallation — adapté au POC 100% hors-ligne.
 */
object PhotoStorage {

    private const val PHOTOS_DIR = "photos"
    private const val FILE_PREFIX = "FLORA_"
    private const val FILE_EXTENSION = ".jpg"

    /** Répertoire des photos, créé si nécessaire. */
    fun photosDir(context: Context): File =
        File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }

    /** Nouveau fichier cible, nommé d'après l'horodatage de capture. */
    fun newPhotoFile(context: Context): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(Date())
        return File(photosDir(context), "$FILE_PREFIX$timestamp$FILE_EXTENSION")
    }

    fun webpFileFor(source: File): File =
        File(source.parentFile, "${source.nameWithoutExtension}.webp")

    fun thumbnailFileFor(full: File): File =
        File(full.parentFile, "${full.nameWithoutExtension}.thumb.webp")

    fun deleteWithThumbnail(path: String) {
        if (path.isEmpty()) return
        val full = File(path)
        full.delete()
        thumbnailFileFor(full).delete()
    }

    /** Supprime toutes les photos stockées (purge au changement de compte). */
    fun clearAll(context: Context) {
        photosDir(context).listFiles()?.forEach { it.delete() }
    }

    /** Liste des photos existantes, des plus récentes aux plus anciennes. */
    fun listPhotos(context: Context): List<File> =
        photosDir(context)
            .listFiles { file -> file.isFile && file.name.endsWith(FILE_EXTENSION) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
