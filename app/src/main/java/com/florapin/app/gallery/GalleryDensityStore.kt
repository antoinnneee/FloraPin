package com.florapin.app.gallery

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Densité de la grille de la galerie (TÂCHE 6.8), réglable par l'utilisateur.
 *
 * En portrait téléphone, chaque palier impose un nombre distinct de colonnes
 * pour que le changement reste visible même sur les écrans étroits. Sur les
 * écrans larges et en paysage, [minCellSize] conserve une grille adaptative.
 */
enum class GalleryDensity(
    val label: String,
    val minCellSize: Dp,
    val phonePortraitColumns: Int,
) {
    /** Dense : petites vignettes, davantage de colonnes. */
    COMPACT("Compacte", 100.dp, phonePortraitColumns = 3),

    /** Palier par défaut photo-first : deux grandes colonnes sur téléphone. */
    COMFORTABLE("Confort", 150.dp, phonePortraitColumns = 2),

    /** Grandes vignettes, moins de colonnes. */
    LARGE("Grande", 220.dp, phonePortraitColumns = 1),
}

/**
 * Préférence locale « densité de la grille de la galerie » (réglage par appareil,
 * TÂCHE 6.8). Store dédié (prefs `florapin_gallery`) : ne partage pas le fichier
 * `florapin_sync` et n'appelle jamais `.clear()`.
 */
class GalleryDensityStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Densité enregistrée, ou le défaut si aucune (ou valeur inconnue). */
    fun density(): GalleryDensity {
        val name = prefs.getString(KEY, null) ?: return DEFAULT
        return runCatching { GalleryDensity.valueOf(name) }.getOrDefault(DEFAULT)
    }

    /** Persiste le palier de densité choisi. */
    fun setDensity(density: GalleryDensity) {
        prefs.edit().putString(KEY, density.name).apply()
    }

    private companion object {
        const val PREFS = "florapin_gallery"
        const val KEY = "grid_density"
        val DEFAULT = GalleryDensity.COMFORTABLE
    }
}
