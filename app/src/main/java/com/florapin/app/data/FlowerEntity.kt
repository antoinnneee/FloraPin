package com.florapin.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Une fleur capturée : chemin de l'image (fichier local), position GPS
 * optionnelle, horodatage et notes libres.
 *
 * L'image n'est pas stockée en base — seul son chemin l'est (le fichier vit
 * dans le stockage privé, voir [com.florapin.app.capture.PhotoStorage]).
 */
@Entity(tableName = "flowers")
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
)
