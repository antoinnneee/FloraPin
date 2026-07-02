package com.florapin.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

/**
 * Une fleur capturée : chemin de l'image (fichier local), position GPS
 * optionnelle, horodatage et notes libres.
 *
 * L'image n'est pas stockée en base — seul son chemin l'est (le fichier vit
 * dans le stockage privé, voir [com.florapin.app.capture.PhotoStorage]).
 */
@Entity(
    tableName = "flowers",
    // Un serverId ne peut désigner qu'une seule ligne (anti-doublon de sync).
    // Les fleurs locales non synchronisées (serverId NULL) ne sont pas
    // contraintes : SQLite traite les NULL comme distincts.
    indices = [Index(value = ["serverId"], unique = true)],
)
data class FlowerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Chemin absolu du fichier image dans le stockage privé. */
    val imagePath: String,

    /** Latitude en degrés décimaux, ou null si la position était indisponible. */
    val latitude: Double? = null,

    /** Longitude en degrés décimaux, ou null. */
    val longitude: Double? = null,

    /** Précision horizontale en mètres, ou null. */
    val accuracyMeters: Float? = null,

    /** Date de capture (epoch millis). */
    val createdAt: Long,

    /** Notes saisies par l'utilisateur (vide par défaut). */
    val notes: String = "",

    /** Espèce (nom) renseignée/identifiée, ou null. */
    val species: String? = null,

    /** FK vers le référentiel d'espèces (NODE-128), null si non rapprochée. */
    val speciesId: String? = null,

    /** Nom scientifique résolu (cache léger pour l'affichage hors-ligne). */
    val speciesScientificName: String? = null,

    /** Nom commun résolu (cache léger). */
    val speciesCommonName: String? = null,

    /** Étiquettes libres (converties en chaîne pour Room, voir [Converters]). */
    val tags: List<String> = emptyList(),

    /**
     * Visibilité (NODE-137) : 'private' (défaut) ou 'friends' (publiée au flux
     * d'amis). Pilotée par le toggle « Publier sur mon flux d'amis ».
     */
    val visibility: String = "private",

    /** Diffusion du GPS au flux d'amis quand [visibility] = 'friends' (NODE-137). */
    val feedIncludeGps: Boolean = true,

    // --- Champs de synchronisation (NODE-43) ---

    /** Identifiant serveur (UUID) une fois synchronisée ; null si locale seule. */
    val serverId: String? = null,

    /** Propriétaire serveur (UUID) ; null pour une capture locale non synchronisée. */
    val ownerId: String? = null,

    /** État de sync : voir [SyncState] (stocké par nom). */
    val syncState: String = SyncState.PENDING.name,

    /** Dernière modification locale (epoch millis) — base de réconciliation. */
    val updatedAt: Long = 0,

    /** Suppression logique locale (epoch millis), ou null. */
    val deletedAt: Long? = null,

    /**
     * Vrai si l'upload de l'image a échoué après la création côté serveur (I9) :
     * la fleur est SYNCED (serverId connu) mais son image doit être retentée au
     * prochain sync.
     */
    val imagePendingUpload: Boolean = false,

    /**
     * URL (présignée) de l'image côté serveur, pour les fleurs distantes dont
     * le fichier n'est pas téléchargé localement (NODE-53). Null pour une fleur
     * capturée localement (l'image vit alors dans [imagePath]).
     */
    val remoteImageUrl: String? = null,

    /**
     * URL (présignée) de la miniature WebP côté serveur, pour afficher un preview
     * léger en galerie/feed sans charger l'image pleine résolution (NODE-53).
     * Null pour une capture locale ou une fleur antérieure au réencodage serveur.
     */
    val remoteThumbnailUrl: String? = null,
)

/** État de synchronisation d'une fleur locale. */
enum class SyncState {
    PENDING,
    SYNCED,
    FAILED,
}

/**
 * Source d'image à fournir à Coil : le fichier local s'il existe, sinon l'URL
 * distante (fleur d'un autre appareil). Null si aucune image disponible.
 */
fun FlowerEntity.imageModel(): Any? =
    if (imagePath.isNotEmpty()) File(imagePath) else remoteImageUrl

/**
 * Source d'image légère (preview) pour les listes (galerie/feed/albums) : le
 * fichier local s'il existe, sinon la miniature distante, et à défaut l'image
 * pleine résolution distante. Le détail, lui, utilise [imageModel] (plein).
 */
fun FlowerEntity.thumbnailModel(): Any? =
    if (imagePath.isNotEmpty()) File(imagePath)
    else remoteThumbnailUrl ?: remoteImageUrl
