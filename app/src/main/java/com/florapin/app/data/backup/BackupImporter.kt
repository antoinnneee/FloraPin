package com.florapin.app.data.backup

import android.content.Context
import com.florapin.app.capture.PhotoStorage
import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.PhotoRepository
import com.squareup.moshi.Moshi
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Importe une sauvegarde ZIP (TÂCHE 1.5) produite par [BackupExporter].
 *
 * L'import est une FUSION idempotente, jamais un écrasement :
 *  - les albums sont dédoublonnés par `clientId` ;
 *  - les fleurs par `serverId` si connu, sinon par date de capture (même
 *    heuristique que la détection de doublon de sync, [FlowerRepository.findLocalTwin]) ;
 *  - les photos par `serverId` si connu, sinon par (fleur, position).
 *
 * Les champs de synchronisation (`serverId`, `syncState`, `updatedAt`…) sont
 * restaurés tels quels : une fleur déjà synchronisée reste SYNCED et n'est pas
 * re-poussée. Les identifiants LOCAUX changent (auto-générés) : on remappe les
 * relations (appartenances d'album, photos → fleur) via des tables de
 * correspondance.
 *
 * Le ZIP est lu en flux ; chaque image est d'abord extraite dans un dossier
 * temporaire (tampon, jamais l'image entière en mémoire), puis seuls les
 * fichiers réellement rattachés à une nouvelle ligne sont déplacés vers le
 * stockage privé.
 */
class BackupImporter(
    private val flowers: FlowerRepository,
    private val albums: AlbumRepository,
    private val photos: PhotoRepository,
    private val photosDir: File,
    private val tempDir: File,
    private val moshi: Moshi = Moshi.Builder().build(),
) {

    /**
     * Lit l'archive depuis [input] (flux d'un document SAF) et fusionne son
     * contenu dans la base locale. [input] n'est pas fermé ici.
     *
     * @throws IOException si l'archive est illisible ou ne contient pas de
     *   manifeste valide.
     */
    suspend fun import(input: InputStream): BackupImportResult {
        val staging = File(tempDir, "backup_import_${System.nanoTime()}").apply { mkdirs() }
        try {
            val manifest = extract(input, staging)
                ?: throw IOException("Archive invalide : manifeste absent ou illisible.")
            return merge(manifest, File(staging, BackupManifest.PHOTOS_DIR))
        } finally {
            staging.deleteRecursively()
        }
    }

    /**
     * Décompresse l'archive : le manifeste est parsé en mémoire (métadonnées
     * légères), les images sont écrites en flux dans `staging/photos/`.
     */
    private fun extract(input: InputStream, staging: File): BackupManifest? {
        var manifest: BackupManifest? = null
        val photosStaging = File(staging, BackupManifest.PHOTOS_DIR).apply { mkdirs() }
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == BackupManifest.MANIFEST_NAME -> {
                        val json = zip.readBytes().toString(Charsets.UTF_8)
                        manifest = runCatching {
                            moshi.adapter(BackupManifest::class.java).fromJson(json)
                        }.getOrNull()
                    }
                    name.startsWith("${BackupManifest.PHOTOS_DIR}/") -> {
                        // Nom de base uniquement (anti-« zip slip » : on ignore
                        // toute composante de chemin de l'entrée).
                        val fileName = name.substringAfterLast('/')
                        if (fileName.isNotBlank()) {
                            File(photosStaging, fileName).outputStream().use { out ->
                                zip.copyTo(out)
                            }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return manifest
    }

    private suspend fun merge(manifest: BackupManifest, photosStaging: File): BackupImportResult {
        // --- Fleurs : dédoublonnage serverId, puis date de capture ---
        val existingFlowers = flowers.allForBackup()
        val flowerByServerId = existingFlowers
            .mapNotNull { f -> f.serverId?.let { it to f.id } }
            .toMap()
        val localFlowerByCreatedAt = existingFlowers
            .filter { it.serverId == null }
            .associate { it.createdAt to it.id }

        // Correspondance id LOCAL d'origine → id LOCAL effectif (existant ou créé).
        val flowerIdMap = HashMap<Long, Long>()
        var flowersAdded = 0
        var flowersSkipped = 0
        for (bf in manifest.flowers) {
            val existingId = when {
                bf.serverId != null -> flowerByServerId[bf.serverId]
                else -> localFlowerByCreatedAt[bf.createdAt]
            }
            if (existingId != null) {
                flowerIdMap[bf.id] = existingId
                flowersSkipped++
            } else {
                val imagePath = copyImage(bf.imageFile, photosStaging)
                val newId = flowers.insert(bf.toEntity(imagePath))
                flowerIdMap[bf.id] = newId
                flowersAdded++
            }
        }

        // --- Albums : dédoublonnage par clientId ---
        val albumByClientId = albums.allActive().associate { it.clientId to it.id }
        val albumIdMap = HashMap<Long, Long>()
        var albumsAdded = 0
        var albumsSkipped = 0
        for (ba in manifest.albums) {
            val existingId = albumByClientId[ba.clientId]
            if (existingId != null) {
                albumIdMap[ba.id] = existingId
                albumsSkipped++
            } else {
                val newId = albums.insert(ba.toEntity())
                albumIdMap[ba.id] = newId
                albumsAdded++
            }
        }

        // --- Appartenances : remappage des deux extrémités ---
        for (ref in manifest.crossRefs) {
            val albumId = albumIdMap[ref.albumId] ?: continue
            val flowerId = flowerIdMap[ref.flowerId] ?: continue
            albums.addCrossRefForBackup(albumId, flowerId)
        }

        // --- Photos : dédoublonnage serverId, puis (fleur, position) ---
        val existingPhotos = photos.allForBackup()
        val photoServerIds = existingPhotos.mapNotNull { it.serverId }.toHashSet()
        val existingPhotoKeys = existingPhotos
            .filter { it.serverId == null }
            .map { it.flowerLocalId to it.position }
            .toHashSet()
        var photosAdded = 0
        var photosSkipped = 0
        for (bp in manifest.photos) {
            val flowerId = flowerIdMap[bp.flowerLocalId] ?: continue // fleur orpheline
            val isDuplicate = when {
                bp.serverId != null -> bp.serverId in photoServerIds
                else -> (flowerId to bp.position) in existingPhotoKeys
            }
            if (isDuplicate) {
                photosSkipped++
                continue
            }
            val imagePath = copyImage(bp.imageFile, photosStaging)
            photos.insert(bp.toEntity(flowerId, imagePath))
            bp.serverId?.let { photoServerIds.add(it) }
            existingPhotoKeys.add(flowerId to bp.position)
            photosAdded++
        }

        return BackupImportResult(
            flowersAdded = flowersAdded,
            flowersSkipped = flowersSkipped,
            albumsAdded = albumsAdded,
            albumsSkipped = albumsSkipped,
            photosAdded = photosAdded,
            photosSkipped = photosSkipped,
        )
    }

    /**
     * Déplace l'image [fileName] du dossier de staging vers le stockage privé et
     * renvoie son chemin absolu. Renvoie "" si aucun fichier n'est associé (fleur
     * distante seule) ou si le fichier est absent de l'archive.
     */
    private fun copyImage(fileName: String?, photosStaging: File): String {
        if (fileName.isNullOrBlank()) return ""
        val source = File(photosStaging, fileName)
        if (!source.exists()) return ""
        photosDir.mkdirs()
        val target = File(photosDir, fileName)
        source.copyTo(target, overwrite = true)
        return target.absolutePath
    }

    companion object {
        /** Câble l'importeur sur les repositories et le stockage privé. */
        fun from(context: Context): BackupImporter {
            val app = context.applicationContext
            return BackupImporter(
                flowers = FlowerRepository.from(app),
                albums = AlbumRepository.from(app),
                photos = PhotoRepository.from(app),
                photosDir = PhotoStorage.photosDir(app),
                tempDir = app.cacheDir,
            )
        }
    }
}
