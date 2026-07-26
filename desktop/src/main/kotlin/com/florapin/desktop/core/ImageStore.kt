package com.florapin.desktop.core

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage

/**
 * Téléchargement, mise en cache et décodage des images.
 *
 * Deux particularités dictent la conception :
 *
 * - Les URLs de photos sont **présignées** et changent à chaque réponse de
 *   l'API. Les utiliser telles quelles comme clé de cache reviendrait à tout
 *   retélécharger à chaque rafraîchissement ; la clé est donc dérivée du seul
 *   chemin de l'objet, stable dans le temps.
 * - Les photos sont encodées en **WebP**, que `ImageIO` ne sait pas lire sur un
 *   JDK standard. Le décodage passe par Skia, déjà embarqué par Compose
 *   Desktop, qui gère WebP, JPEG et PNG.
 */
object ImageStore {

    /**
     * Cache mémoire borné par le poids approximatif des bitmaps décodés
     * (4 octets par pixel), et non par leur nombre : une grille de miniatures
     * et une visionneuse plein écran n'ont pas le même coût unitaire.
     */
    private const val MEMORY_BUDGET_BYTES = 512L * 1024 * 1024

    /** Au-delà, le cache disque est purgé des entrées les plus anciennes. */
    private const val DISK_BUDGET_BYTES = 2L * 1024 * 1024 * 1024

    private val memory = LinkedHashMap<String, ImageBitmap>(64, 0.75f, true)
    private var memoryBytes = 0L
    private val memoryLock = Any()

    /** Une entrée par image : deux vignettes identiques ne se téléchargent qu'une fois. */
    private val inFlight = mutableMapOf<String, Mutex>()
    private val inFlightLock = Any()

    private val client: OkHttpClient get() = DesktopNetwork.httpClient

    /** Bitmap prêt à afficher, depuis la mémoire, le disque, puis le réseau. */
    suspend fun load(url: String): ImageBitmap? {
        val key = cacheKey(url)
        synchronized(memoryLock) { memory[key] }?.let { return it }

        val file = fetch(url) ?: return null
        val bitmap = withContext(Dispatchers.Default) { decode(file) } ?: return null
        remember(key, bitmap)
        return bitmap
    }

    /**
     * Fichier local de l'image, téléchargé si nécessaire. Utilisé par l'export :
     * une photo déjà consultée est alors copiée sans repasser par le réseau.
     */
    suspend fun fetch(url: String): File? {
        val key = cacheKey(url)
        val file = File(DesktopConfig.imageCacheDir, key)
        if (file.isFile && file.length() > 0) return file

        val mutex = synchronized(inFlightLock) { inFlight.getOrPut(key) { Mutex() } }
        return mutex.withLock {
            if (file.isFile && file.length() > 0) return@withLock file
            val downloaded = withContext(Dispatchers.IO) { download(url, file) }
            synchronized(inFlightLock) { inFlight.remove(key) }
            if (downloaded) file else null
        }
    }

    /** Vrai si l'image est déjà décodée : évite un état de chargement inutile. */
    fun peek(url: String): ImageBitmap? =
        synchronized(memoryLock) { memory[cacheKey(url)] }

    private fun download(url: String, target: File): Boolean = runCatching {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            target.parentFile?.mkdirs()
            // Écriture dans un fichier temporaire puis renommage : un
            // téléchargement interrompu ne laisse pas d'image tronquée en cache.
            val tmp = File(target.parentFile, "${target.name}.part")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }.getOrDefault(false).also { if (it) trimDiskCache() }

    private fun decode(file: File): ImageBitmap? = runCatching {
        SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    }.getOrNull()

    private fun remember(key: String, bitmap: ImageBitmap) {
        val weight = bitmap.width.toLong() * bitmap.height.toLong() * 4
        synchronized(memoryLock) {
            memory[key] = bitmap
            memoryBytes += weight
            val iterator = memory.entries.iterator()
            while (memoryBytes > MEMORY_BUDGET_BYTES && iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key == key) continue
                memoryBytes -= entry.value.width.toLong() * entry.value.height.toLong() * 4
                iterator.remove()
            }
        }
    }

    /**
     * Clé stable : empreinte du chemin de l'URL, signature de téléchargement
     * exclue. L'extension d'origine est conservée pour que les fichiers du
     * cache restent identifiables et réutilisables tels quels à l'export.
     *
     * `internal` plutôt que `private` : cette normalisation est le point
     * délicat du cache et mérite d'être couverte par un test.
     */
    internal fun cacheKey(url: String): String {
        val path = runCatching { URI(url).path }.getOrNull() ?: url.substringBefore('?')
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val extension = path.substringAfterLast('.', "").take(5)
            .filter { it.isLetterOrDigit() }
        return if (extension.isEmpty()) digest else "$digest.$extension"
    }

    /** Purge les fichiers les plus anciens quand le cache dépasse son budget. */
    private fun trimDiskCache() {
        runCatching {
            val files = DesktopConfig.imageCacheDir.listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= DISK_BUDGET_BYTES) return
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (total <= DISK_BUDGET_BYTES) return
                total -= file.length()
                file.delete()
            }
        }
    }

    /** Vide le cache disque (écran de réglages). Renvoie les octets libérés. */
    fun clearDiskCache(): Long {
        val files = DesktopConfig.imageCacheDir.listFiles()?.filter { it.isFile } ?: return 0
        val freed = files.sumOf { it.length() }
        files.forEach { it.delete() }
        synchronized(memoryLock) {
            memory.clear()
            memoryBytes = 0
        }
        return freed
    }

    /** Poids actuel du cache disque, affiché dans les réglages. */
    fun diskCacheBytes(): Long =
        DesktopConfig.imageCacheDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0
}
