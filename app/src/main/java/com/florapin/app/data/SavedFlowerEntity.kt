package com.florapin.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.File

/**
 * Fleur d'un ami enregistrée dans « Ma sélection » (TÂCHE 3.11) : favori PRIVÉ et
 * LOCAL (device-first, aucune API dédiée).
 *
 * La fleur d'un ami n'existe pas dans [FlowerEntity] (elle appartient à un autre
 * appareil). On persiste donc un SNAPSHOT autonome — id serveur, nom d'espèce, nom
 * de l'ami, miniature mise en cache localement — pour continuer à l'afficher hors
 * ligne ou même si le partage est ensuite révoqué. Les URLs présignées (qui
 * expirent) ne servent que de repli si la mise en cache échoue.
 */
@Entity(
    tableName = "saved_flowers",
    // Un id serveur ne peut désigner qu'une seule ligne (ré-enregistrement idempotent).
    indices = [Index(value = ["serverId"], unique = true)],
)
data class SavedFlowerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Id serveur de la fleur enregistrée (clé stable, unique). */
    val serverId: String,

    /** Propriétaire serveur (l'ami), pour rappel/regroupement éventuel. */
    val ownerId: String? = null,

    /** Nom de l'ami au moment de l'enregistrement (snapshot d'affichage). */
    val ownerName: String? = null,

    /** Nom d'espèce affiché (snapshot), ou null si inconnu. */
    val species: String? = null,

    /**
     * Chemin absolu de la miniature mise en cache localement, ou vide si la mise
     * en cache a échoué (on retombe alors sur [remoteThumbnailUrl]/[remoteImageUrl]).
     */
    val imagePath: String = "",

    /** URL présignée de la miniature au moment de l'enregistrement (repli, expire). */
    val remoteThumbnailUrl: String? = null,

    /** URL présignée pleine résolution au moment de l'enregistrement (repli, expire). */
    val remoteImageUrl: String? = null,

    val latitude: Double? = null,
    val longitude: Double? = null,

    /** Date d'enregistrement (epoch millis) — tri « plus récent d'abord ». */
    val savedAt: Long,
)

/**
 * Source d'image pour Coil : le fichier local mis en cache s'il existe, sinon la
 * miniature distante, et à défaut l'image pleine résolution distante (ces URLs
 * peuvent avoir expiré, d'où la mise en cache à l'enregistrement).
 */
fun SavedFlowerEntity.imageModel(): Any? =
    if (imagePath.isNotEmpty()) File(imagePath) else remoteThumbnailUrl ?: remoteImageUrl
