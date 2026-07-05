package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs des réactions sur une fleur (TÂCHE 3.5). */

/**
 * Jeu (fermé) de réactions, aligné sur le backend. Le code est stocké/transmis ;
 * l'emoji n'est qu'un rendu. `HEART` (❤️) est la réaction par défaut : posée au
 * simple tap et pour les cœurs historiques (NODE-140). Les 7 autres s'obtiennent
 * via le sélecteur (appui long).
 */
object Reactions {
    const val HEART = "heart"

    /** Réactions proposées dans le sélecteur (appui long), dans l'ordre d'affichage. */
    val PICKER: List<Pair<String, String>> = listOf(
        "love" to "😍",
        "blossom" to "🌸",
        "rose" to "🌹",
        "daisy" to "🌼",
        "lavender" to "🪻",
        "magnify" to "🔍",
        "thumbsup" to "👍",
    )

    private val EMOJI: Map<String, String> = mapOf(HEART to "❤️") + PICKER.toMap()

    /** Emoji d'un code de réaction ; repli sur le cœur pour un code inconnu. */
    fun emoji(code: String?): String = EMOJI[code] ?: "❤️"
}

/** Corps de POST flowers/{id}/like quand une réaction précise est choisie. */
@JsonClass(generateAdapter = true)
data class ReactionRequest(
    val reaction: String,
)

/** Un « liker » renvoyé par GET flowers/{id}/likes : identifiant, nom, réaction. */
@JsonClass(generateAdapter = true)
data class LikerDto(
    val userId: String,
    /** Nom d'affichage du liker (peut être vide). */
    val displayName: String = "",
    /** Type de réaction posé (TÂCHE 3.5) ; défaut cœur pour la compat ascendante. */
    val reaction: String = Reactions.HEART,
)
