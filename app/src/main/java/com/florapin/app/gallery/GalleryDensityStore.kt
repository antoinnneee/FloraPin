package com.florapin.app.gallery

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Densité de la grille de la galerie (TÂCHE 6.8), réglable par l'utilisateur.
 *
 * Chaque palier fixe la taille minimale d'une vignette : plus la taille est
 * petite, plus il tient de colonnes à l'écran (grille dense), et inversement. La
 * grille reste adaptative ([androidx.compose.foundation.lazy.grid.GridCells.Adaptive]),
 * donc le nombre exact de colonnes dépend de la largeur de l'écran.
 */
enum class GalleryDensity(val label: String, val minCellSize: Dp) {
    /** Dense : petites vignettes, davantage de colonnes. */
    COMPACT("Compacte", 90.dp),

    /** Palier par défaut, historique de la galerie. */
    COMFORTABLE("Confort", 120.dp),

    /** Grandes vignettes, moins de colonnes. */
    LARGE("Grande", 160.dp),
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
