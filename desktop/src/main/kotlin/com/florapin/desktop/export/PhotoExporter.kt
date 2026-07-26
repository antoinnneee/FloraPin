package com.florapin.desktop.export

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.fullPhotoUrls
import com.florapin.desktop.core.ImageStore
import java.io.File
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image as SkiaImage

/** Arborescence produite dans le dossier de destination. */
enum class ExportLayout(val label: String, val description: String) {
    FLAT("Un seul dossier", "Toutes les photos côte à côte"),
    BY_DATE("Par année et mois", "2026 / 07-juillet / …"),
    BY_SPECIES("Par espèce", "Un dossier par espèce identifiée"),
    BY_ALBUM("Par album", "Un dossier par album ; le reste dans « Hors album »"),
}

/** Format des fichiers écrits. */
enum class ExportFormat(val label: String, val description: String) {
    ORIGINAL("D'origine (WebP)", "Copie exacte, sans perte de qualité supplémentaire"),
    JPEG("JPEG", "Compatible avec tous les logiciels et cadres photo"),
}

data class ExportOptions(
    val destination: File,
    val layout: ExportLayout = ExportLayout.BY_DATE,
    val format: ExportFormat = ExportFormat.ORIGINAL,
    /** Toutes les photos de chaque fiche, ou seulement l'image de couverture. */
    val allPhotos: Boolean = true,
    /** Récapitulatif CSV (espèce, date, position, notes) à la racine. */
    val writeCatalog: Boolean = true,
)

/**
 * Copie des photos vers un dossier choisi par l'utilisateur — la raison d'être
 * du compagnon : sortir ses souvenirs de l'application.
 *
 * Le téléchargement passe par [ImageStore], donc une photo déjà consultée est
 * copiée depuis le cache sans nouvel appel réseau. Les métadonnées (espèce,
 * date, position, notes) ne survivraient pas à une simple copie de fichiers :
 * un récapitulatif CSV les accompagne, lisible par un tableur.
 */
class PhotoExporter(private val scope: CoroutineScope) {

    var running by mutableStateOf(false)
        private set
    var total by mutableStateOf(0)
        private set
    var done by mutableStateOf(0)
        private set
    var currentLabel by mutableStateOf("")
        private set
    var failures by mutableStateOf(0)
        private set

    /** Compte rendu de fin, affiché jusqu'à la fermeture de la fenêtre. */
    var report by mutableStateOf<String?>(null)

    private var job: Job? = null

    val progress: Float get() = if (total == 0) 0f else done.toFloat() / total

    fun cancel() {
        job?.cancel()
    }

    fun start(flowers: List<FlowerDto>, albums: List<AlbumDto>, options: ExportOptions) {
        if (running) return
        job = scope.launch {
            running = true
            done = 0
            failures = 0
            report = null
            val tasks = buildTasks(flowers, albums, options)
            total = tasks.size
            val catalog = StringBuilder()
            if (options.writeCatalog) catalog.appendLine(CSV_HEADER)

            try {
                withContext(Dispatchers.IO) { options.destination.mkdirs() }
                for (task in tasks) {
                    currentLabel = task.fileName
                    val ok = copyOne(task, options)
                    if (!ok) failures++
                    if (options.writeCatalog) catalog.appendLine(task.csvLine(ok))
                    done++
                }
                if (options.writeCatalog) {
                    withContext(Dispatchers.IO) {
                        File(options.destination, "florapin-export.csv")
                            .writeText(catalog.toString(), Charsets.UTF_8)
                    }
                }
                report = buildString {
                    append("${done - failures} photo(s) exportée(s) vers ${options.destination}")
                    if (failures > 0) append(" — $failures échec(s)")
                }
            } catch (_: CancellationException) {
                report = "Export interrompu — $done photo(s) déjà copiée(s)"
            } catch (e: Exception) {
                report = "Export interrompu : ${e.message ?: "erreur inattendue"}"
            } finally {
                running = false
                currentLabel = ""
            }
        }
    }

    private suspend fun copyOne(task: ExportTask, options: ExportOptions): Boolean {
        val source = ImageStore.fetch(task.url) ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(options.destination, task.relativeDir).apply { mkdirs() }
                val target = uniqueFile(dir, task.fileName)
                when (options.format) {
                    ExportFormat.ORIGINAL -> source.copyTo(target, overwrite = false)
                    ExportFormat.JPEG -> {
                        val encoded = SkiaImage.makeFromEncoded(source.readBytes())
                            .encodeToData(EncodedImageFormat.JPEG, JPEG_QUALITY)
                            ?: return@runCatching false
                        target.writeBytes(encoded.bytes)
                    }
                }
                true
            }.getOrDefault(false)
        }
    }

    private fun buildTasks(
        flowers: List<FlowerDto>,
        albums: List<AlbumDto>,
        options: ExportOptions,
    ): List<ExportTask> {
        // Un album par fleur pour l'arborescence BY_ALBUM ; une fleur présente
        // dans plusieurs albums est rangée dans le premier, pour ne pas la
        // dupliquer sur le disque de l'utilisateur.
        val albumOf = mutableMapOf<String, String>()
        albums.forEach { album ->
            album.flowerIds.forEach { id -> albumOf.putIfAbsent(id, album.name) }
        }

        return flowers.flatMap { flower ->
            val urls = if (options.allPhotos) {
                flower.fullPhotoUrls()
            } else {
                listOf(flower.fullPhotoUrls().first())
            }
            val taken = parseDate(flower.takenAt)
            val speciesLabel = flower.speciesRef?.commonName
                ?: flower.speciesRef?.scientificName
                ?: flower.species
            val dir = when (options.layout) {
                ExportLayout.FLAT -> ""
                ExportLayout.BY_DATE -> taken?.let {
                    "${it.year}/${"%02d".format(it.monthValue)}-${MONTHS[it.monthValue - 1]}"
                } ?: "date-inconnue"
                ExportLayout.BY_SPECIES -> sanitize(speciesLabel ?: "espèce-inconnue")
                ExportLayout.BY_ALBUM -> sanitize(albumOf[flower.id] ?: "Hors album")
            }
            urls.mapIndexed { index, url ->
                val stamp = taken?.format(FILE_STAMP) ?: "sans-date"
                val suffix = if (urls.size > 1) "-${index + 1}" else ""
                val name = sanitize(
                    listOfNotNull(stamp, speciesLabel?.let { sanitize(it) }).joinToString("_"),
                )
                val extension = when (options.format) {
                    ExportFormat.JPEG -> "jpg"
                    ExportFormat.ORIGINAL -> url.substringBefore('?')
                        .substringAfterLast('.', "webp")
                        .take(5)
                        .ifBlank { "webp" }
                }
                ExportTask(
                    flower = flower,
                    url = url,
                    relativeDir = dir,
                    fileName = "$name$suffix.$extension",
                    speciesLabel = speciesLabel,
                )
            }
        }
    }

    private data class ExportTask(
        val flower: FlowerDto,
        val url: String,
        val relativeDir: String,
        val fileName: String,
        val speciesLabel: String?,
    ) {
        fun csvLine(exported: Boolean): String = listOf(
            if (relativeDir.isEmpty()) fileName else "$relativeDir/$fileName",
            speciesLabel ?: "",
            flower.takenAt,
            flower.latitude?.toString() ?: "",
            flower.longitude?.toString() ?: "",
            flower.tags.joinToString(" "),
            flower.notes,
            if (exported) "ok" else "échec",
        ).joinToString(";") { field ->
            // Échappement CSV : les notes contiennent volontiers des retours
            // à la ligne et des points-virgules.
            "\"" + field.replace("\"", "\"\"").replace("\n", " ").replace("\r", "") + "\""
        }
    }

    private companion object {
        const val JPEG_QUALITY = 92
        const val CSV_HEADER = "fichier;espece;prise_le;latitude;longitude;tags;notes;statut"
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
        val MONTHS = listOf(
            "janvier", "fevrier", "mars", "avril", "mai", "juin",
            "juillet", "aout", "septembre", "octobre", "novembre", "decembre",
        )

        fun parseDate(iso: String): OffsetDateTime? =
            runCatching { OffsetDateTime.parse(iso) }.getOrNull()

        fun sanitize(raw: String): String = ExportNaming.sanitize(raw)

        fun uniqueFile(dir: File, name: String): File = ExportNaming.uniqueFile(dir, name)
    }
}

/**
 * Fabrication des noms de fichiers, isolée du reste pour être vérifiable sans
 * réseau ni interface : c'est la partie de l'export qui concentre les cas
 * limites — caractères interdits par Windows, collisions, libellés vides.
 */
internal object ExportNaming {

    /**
     * Retire ce que Windows interdit dans un nom de fichier, ainsi que les
     * points et espaces finaux : l'Explorateur les supprime silencieusement,
     * ce qui produirait des fichiers au nom différent de celui annoncé.
     */
    fun sanitize(raw: String): String = raw.trim()
        .replace(Regex("[\\\\/:*?\"<>|]"), "-")
        .replace(Regex("\\s+"), " ")
        .trim('.', ' ')
        .take(80)
        .ifBlank { "sans-nom" }

    /** Ajoute un suffixe numérique plutôt que d'écraser un fichier existant. */
    fun uniqueFile(dir: File, name: String): File {
        val candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.')
        val extension = name.substringAfterLast('.', "")
        var index = 2
        while (true) {
            val next = File(dir, "$base ($index)" + if (extension.isEmpty()) "" else ".$extension")
            if (!next.exists()) return next
            index++
        }
    }
}
