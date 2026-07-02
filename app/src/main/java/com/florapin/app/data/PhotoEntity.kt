package com.florapin.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

/**
 * Photo additionnelle d'une fleur (NODE-107). La photo principale (couverture)
 * reste portée par [FlowerEntity.imagePath] ; cette table stocke les photos
 * supplémentaires et celles reçues du serveur. Champs de sync alignés sur
 * [FlowerEntity].
 */
@Entity(
    tableName = "flower_photos",
    indices = [Index("flowerLocalId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Fleur locale propriétaire. */
    val flowerLocalId: Long,

    /** Id serveur de la photo (null tant que non poussée). */
    val serverId: String? = null,

    /** Chemin du fichier local, ou vide pour une photo distante. */
    val imagePath: String = "",

    /** URL présignée de lecture (photo distante). */
    val remoteUrl: String? = null,

    /** URL présignée de la miniature WebP (preview), ou null. */
    val remoteThumbnailUrl: String? = null,

    val position: Int = 0,
    val isCover: Boolean = false,
    val syncState: String = SyncState.PENDING.name,
    val deletedAt: Long? = null,

    /**
     * Vrai si l'upload du fichier a échoué après la création côté serveur (I9) :
     * la photo est SYNCED (serverId connu) mais son image doit être retentée au
     * prochain sync.
     */
    val imagePendingUpload: Boolean = false,
)

/** Source d'image à fournir à Coil pour une photo : fichier local sinon URL. */
fun PhotoEntity.imageModel(): Any? =
    if (imagePath.isNotEmpty()) File(imagePath) else remoteUrl

/** Source d'image légère (preview) : fichier local, sinon miniature, sinon plein. */
fun PhotoEntity.thumbnailModel(): Any? =
    if (imagePath.isNotEmpty()) File(imagePath)
    else remoteThumbnailUrl ?: remoteUrl
