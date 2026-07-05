package com.florapin.app.data.backup

import com.florapin.app.data.AlbumEntity
import com.florapin.app.data.FlowerAlbumCrossRef
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.PhotoEntity
import com.squareup.moshi.JsonClass

/**
 * Modèle de la sauvegarde locale (export/import ZIP — TÂCHE 1.5).
 *
 * Le ZIP contient un manifeste JSON ([MANIFEST_NAME]) décrivant les entités et,
 * dans un dossier `photos/`, les fichiers images référencés par leur seul nom
 * de base ([BackupFlower.imageFile] / [BackupPhoto.imageFile]) — jamais un
 * chemin absolu, qui n'aurait aucun sens sur un autre appareil.
 *
 * On sérialise des DTO plutôt que les entités Room directement : cela isole le
 * format d'archive du schéma (imagePath absolu remplacé par un nom de fichier),
 * conserve les identifiants LOCAUX d'origine (pour reconstruire les relations à
 * l'import) et préserve les champs de synchronisation (`serverId`, `syncState`,
 * `updatedAt`…) afin que l'import ne re-pousse pas tout en double.
 */
@JsonClass(generateAdapter = true)
data class BackupManifest(
    /** Version du format d'archive (montée si le schéma JSON change). */
    val version: Int = CURRENT_VERSION,
    /** Horodatage de l'export (epoch millis), à titre informatif. */
    val exportedAt: Long,
    val flowers: List<BackupFlower> = emptyList(),
    val albums: List<BackupAlbum> = emptyList(),
    val crossRefs: List<BackupCrossRef> = emptyList(),
    val photos: List<BackupPhoto> = emptyList(),
) {
    companion object {
        /** Version courante du format de sauvegarde. */
        const val CURRENT_VERSION = 1

        /** Nom du manifeste JSON dans le ZIP. */
        const val MANIFEST_NAME = "manifest.json"

        /** Dossier des fichiers images dans le ZIP. */
        const val PHOTOS_DIR = "photos"
    }
}

@JsonClass(generateAdapter = true)
data class BackupFlower(
    /** Identifiant LOCAL d'origine (clé de remappage des relations à l'import). */
    val id: Long,
    /** Nom de base du fichier image dans le ZIP, ou null (fleur distante seule). */
    val imageFile: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Float?,
    val createdAt: Long,
    val notes: String,
    val species: String?,
    val speciesId: String?,
    val speciesScientificName: String?,
    val speciesCommonName: String?,
    val tags: List<String>,
    val visibility: String,
    val feedIncludeGps: Boolean,
    val serverId: String?,
    val ownerId: String?,
    val syncState: String,
    val updatedAt: Long,
    val imagePendingUpload: Boolean,
    val remoteImageUrl: String?,
    val remoteThumbnailUrl: String?,
)

@JsonClass(generateAdapter = true)
data class BackupAlbum(
    val id: Long,
    val serverId: String?,
    val clientId: String,
    val name: String,
    val ownerId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncState: String,
)

@JsonClass(generateAdapter = true)
data class BackupCrossRef(
    val albumId: Long,
    val flowerId: Long,
)

@JsonClass(generateAdapter = true)
data class BackupPhoto(
    val id: Long,
    val flowerLocalId: Long,
    val imageFile: String?,
    val serverId: String?,
    val remoteUrl: String?,
    val remoteThumbnailUrl: String?,
    val position: Int,
    val isCover: Boolean,
    val syncState: String,
    val imagePendingUpload: Boolean,
)

// --- Mapping entités → DTO (export) ---

/** Nom de base du fichier référencé par un chemin, ou null si vide. */
private fun fileNameOrNull(path: String): String? =
    if (path.isBlank()) null else java.io.File(path).name.ifBlank { null }

fun FlowerEntity.toBackup(): BackupFlower = BackupFlower(
    id = id,
    imageFile = fileNameOrNull(imagePath),
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    createdAt = createdAt,
    notes = notes,
    species = species,
    speciesId = speciesId,
    speciesScientificName = speciesScientificName,
    speciesCommonName = speciesCommonName,
    tags = tags,
    visibility = visibility,
    feedIncludeGps = feedIncludeGps,
    serverId = serverId,
    ownerId = ownerId,
    syncState = syncState,
    updatedAt = updatedAt,
    imagePendingUpload = imagePendingUpload,
    remoteImageUrl = remoteImageUrl,
    remoteThumbnailUrl = remoteThumbnailUrl,
)

fun AlbumEntity.toBackup(): BackupAlbum = BackupAlbum(
    id = id,
    serverId = serverId,
    clientId = clientId,
    name = name,
    ownerId = ownerId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = syncState,
)

fun FlowerAlbumCrossRef.toBackup(): BackupCrossRef =
    BackupCrossRef(albumId = albumId, flowerId = flowerId)

fun PhotoEntity.toBackup(): BackupPhoto = BackupPhoto(
    id = id,
    flowerLocalId = flowerLocalId,
    imageFile = fileNameOrNull(imagePath),
    serverId = serverId,
    remoteUrl = remoteUrl,
    remoteThumbnailUrl = remoteThumbnailUrl,
    position = position,
    isCover = isCover,
    syncState = syncState,
    imagePendingUpload = imagePendingUpload,
)

// --- Mapping DTO → entités (import) ---

/**
 * Reconstruit une [FlowerEntity] à insérer. [id] repart à 0 (auto-généré) et
 * [imagePath] pointe vers le fichier fraîchement copié dans le stockage privé
 * (vide si aucune image locale). Tous les champs de sync sont préservés pour ne
 * pas re-pousser la fleur au prochain sync.
 */
fun BackupFlower.toEntity(imagePath: String): FlowerEntity = FlowerEntity(
    id = 0,
    imagePath = imagePath,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyMeters,
    createdAt = createdAt,
    notes = notes,
    species = species,
    speciesId = speciesId,
    speciesScientificName = speciesScientificName,
    speciesCommonName = speciesCommonName,
    tags = tags,
    visibility = visibility,
    feedIncludeGps = feedIncludeGps,
    serverId = serverId,
    ownerId = ownerId,
    syncState = syncState,
    updatedAt = updatedAt,
    deletedAt = null,
    imagePendingUpload = imagePendingUpload,
    remoteImageUrl = remoteImageUrl,
    remoteThumbnailUrl = remoteThumbnailUrl,
)

fun BackupAlbum.toEntity(): AlbumEntity = AlbumEntity(
    id = 0,
    serverId = serverId,
    clientId = clientId,
    name = name,
    ownerId = ownerId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = syncState,
    deletedAt = null,
)

fun BackupPhoto.toEntity(flowerLocalId: Long, imagePath: String): PhotoEntity =
    PhotoEntity(
        id = 0,
        flowerLocalId = flowerLocalId,
        imagePath = imagePath,
        serverId = serverId,
        remoteUrl = remoteUrl,
        remoteThumbnailUrl = remoteThumbnailUrl,
        position = position,
        isCover = isCover,
        syncState = syncState,
        deletedAt = null,
        imagePendingUpload = imagePendingUpload,
    )

/** Bilan d'un export : compteurs affichés à l'utilisateur. */
data class BackupExportResult(
    val flowers: Int,
    val albums: Int,
    val photos: Int,
    val imageFiles: Int,
)

/** Bilan d'un import : ajouts effectués et doublons ignorés (idempotence). */
data class BackupImportResult(
    val flowersAdded: Int,
    val flowersSkipped: Int,
    val albumsAdded: Int,
    val albumsSkipped: Int,
    val photosAdded: Int,
    val photosSkipped: Int,
)
