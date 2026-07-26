package com.florapin.app.detail

/**
 * Séparateur volontairement lisible par les anciennes versions de FloraPin :
 * celles-ci affichent toujours le champ `notes` comme un texte unique, avec un
 * discret séparateur entre les entrées.
 */
private const val OBSERVATION_NOTE_SEPARATOR = "\n\n⁂\n\n"

internal fun decodeObservationNotes(storedNotes: String): List<String> =
    storedNotes
        .split(OBSERVATION_NOTE_SEPARATOR)
        .map(String::trim)
        .filter(String::isNotEmpty)

internal fun encodeObservationNotes(notes: List<String>): String =
    notes
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString(OBSERVATION_NOTE_SEPARATOR)
