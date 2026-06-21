package com.florapin.app.data

import androidx.room.TypeConverter

/**
 * Convertisseurs Room pour les types non primitifs.
 *
 * Les étiquettes ([FlowerEntity.tags]) sont stockées dans une seule colonne TEXT
 * en les joignant par un saut de ligne (séparateur absent des étiquettes).
 */
class Converters {

    @TypeConverter
    fun fromTags(tags: List<String>): String = tags.joinToString("\n")

    @TypeConverter
    fun toTags(raw: String): List<String> =
        if (raw.isEmpty()) emptyList() else raw.split("\n")
}
