package com.florapin.app.data.backup

import android.content.Context
import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.PhotoRepository
import com.squareup.moshi.Moshi
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exporte la bibliothèque locale (données + fichiers images) dans une archive
 * ZIP (TÂCHE 1.5). Filet de sécurité du mode 100 % local : l'utilisateur choisit
 * la destination via le sélecteur de documents (SAF) et [export] écrit dans le
 * flux fourni.
 *
 * Choix de conception (voir les pièges de la tâche) :
 *  - on NE copie PAS le fichier `.db` à chaud (risque WAL) : on sérialise un
 *    dump JSON via les DAO ;
 *  - le ZIP est écrit en flux, chaque image copiée par tampon (jamais l'archive
 *    entière ni une image complète en mémoire).
 */
class BackupExporter(
    private val flowers: FlowerRepository,
    private val albums: AlbumRepository,
    private val photos: PhotoRepository,
    private val moshi: Moshi = Moshi.Builder().build(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {

    /**
     * Écrit l'archive dans [output] (typiquement le flux d'un document SAF).
     * Le flux n'est PAS fermé ici (l'appelant qui l'a ouvert le referme).
     */
    suspend fun export(output: OutputStream): BackupExportResult {
        val flowerRows = flowers.allForBackup()
        val albumRows = albums.allActive()
        val crossRefRows = albums.allCrossRefsForBackup()
        val photoRows = photos.allForBackup()

        val manifest = BackupManifest(
            exportedAt = now(),
            flowers = flowerRows.map { it.toBackup() },
            albums = albumRows.map { it.toBackup() },
            crossRefs = crossRefRows.map { it.toBackup() },
            photos = photoRows.map { it.toBackup() },
        )

        var imageFiles = 0
        ZipOutputStream(output.buffered()).use { zip ->
            // 1. Manifeste JSON en tête d'archive.
            val json = moshi.adapter(BackupManifest::class.java).toJson(manifest)
            zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST_NAME))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // 2. Fichiers images, en flux (dédoublonnés par nom d'entrée : deux
            //    entités ne peuvent pas partager un même fichier, mais on se
            //    prémunit contre une éventuelle collision qui ferait échouer le
            //    ZIP sur une entrée dupliquée).
            val written = HashSet<String>()
            val sources = buildList {
                flowerRows.forEach { f ->
                    if (f.imagePath.isNotEmpty()) add(f.imagePath)
                }
                photoRows.forEach { p ->
                    if (p.imagePath.isNotEmpty()) add(p.imagePath)
                }
            }
            sources.forEach { path ->
                val file = File(path)
                val name = file.name
                if (name.isBlank() || !file.exists() || !written.add(name)) return@forEach
                zip.putNextEntry(ZipEntry("${BackupManifest.PHOTOS_DIR}/$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                imageFiles++
            }
        }

        return BackupExportResult(
            flowers = flowerRows.size,
            albums = albumRows.size,
            photos = photoRows.size,
            imageFiles = imageFiles,
        )
    }

    companion object {
        /** Câble l'exporteur sur les repositories singletons. */
        fun from(context: Context): BackupExporter {
            val app = context.applicationContext
            return BackupExporter(
                flowers = FlowerRepository.from(app),
                albums = AlbumRepository.from(app),
                photos = PhotoRepository.from(app),
            )
        }
    }
}
