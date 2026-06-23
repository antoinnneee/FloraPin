package com.florapin.app.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/**
 * Choix de l'emoji de marqueur en fonction de l'espèce d'une fleur (NODE-112).
 * 🌸 par défaut ; quelques espèces courantes ont leur propre emoji. La détection
 * est insensible à la casse et tolère les noms latins comme vernaculaires
 * (ex. « Rosa », « rose », « rosier » → 🌹).
 */
object FlowerEmoji {
    const val DEFAULT = "🌸"

    /** Mots-clés (en minuscules) → emoji. Premier match retenu. */
    private val byKeyword: List<Pair<String, String>> = listOf(
        "tournesol" to "🌻", "helianthus" to "🌻", "sunflower" to "🌻",
        "rose" to "🌹", "rosa" to "🌹", "rosier" to "🌹",
        "tulipe" to "🌷", "tulipa" to "🌷", "tulip" to "🌷",
        "marguerite" to "🌼", "paquerette" to "🌼", "daisy" to "🌼", "bellis" to "🌼",
        "hibiscus" to "🌺",
        "cactus" to "🌵", "cactaceae" to "🌵",
        "lotus" to "🪷", "nelumbo" to "🪷",
    )

    /** Toutes les valeurs d'emoji distinctes utilisées (pour pré-enregistrer les images). */
    val all: List<String> = (listOf(DEFAULT) + byKeyword.map { it.second }).distinct()

    /** Emoji correspondant à [species], ou [DEFAULT] si inconnu/vide. */
    fun forSpecies(species: String?): String {
        if (species.isNullOrBlank()) return DEFAULT
        val normalized = species.lowercase()
        return byKeyword.firstOrNull { (keyword, _) -> normalized.contains(keyword) }?.second
            ?: DEFAULT
    }
}

/**
 * Rend un emoji dans un bitmap carré transparent, prêt à servir d'icône de
 * marqueur MapLibre. [sizePx] est la taille du bitmap en pixels.
 */
fun emojiToBitmap(emoji: String, sizePx: Int = 72): Bitmap {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx * 0.8f
        textAlign = Paint.Align.CENTER
    }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val metrics = paint.fontMetrics
    val centerX = sizePx / 2f
    val baselineY = sizePx / 2f - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(emoji, centerX, baselineY, paint)
    return bitmap
}
