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
    COMPACT("Compact", 100.dp, phonePortraitColumns = 3),

    /** Palier par défaut photo-first : deux grandes colonnes sur téléphone. */
    COMFORTABLE("Confort", 150.dp, phonePortraitColumns = 2),

    /** Grandes vignettes, moins de colonnes. */
    LARGE("Grande", 220.dp, phonePortraitColumns = 1),
}

/**
 * Les quatre présentations proposées directement depuis l'action d'affichage de
 * l'accueil. Le mode liste n'a pas de densité ; les trois autres réutilisent les
 * paliers historiques de la grille.
 */
enum class GalleryDisplayMode(
    val label: String,
    val density: GalleryDensity?,
) {
    LIST("Liste", null),
    COMPACT("Compact", GalleryDensity.COMPACT),
    COMFORTABLE("Confort", GalleryDensity.COMFORTABLE),
    LARGE("Grande", GalleryDensity.LARGE),
    ;

    val isList: Boolean get() = this == LIST

    companion object {
        fun fromDensity(density: GalleryDensity): GalleryDisplayMode = when (density) {
            GalleryDensity.COMPACT -> COMPACT
            GalleryDensity.COMFORTABLE -> COMFORTABLE
            GalleryDensity.LARGE -> LARGE
        }
    }
}

/**
 * Préférence locale du mode d'affichage de la galerie (réglage par appareil).
 *
 * La première lecture migre implicitement l'ancienne préférence `grid_density`
 * afin de conserver le rendu déjà choisi par les utilisateurs.
 */
class GalleryDisplayModeStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Mode enregistré, ancienne densité migrée, ou Confort par défaut. */
    fun displayMode(): GalleryDisplayMode {
        prefs.getString(MODE_KEY, null)?.let { name ->
            return runCatching { GalleryDisplayMode.valueOf(name) }.getOrDefault(DEFAULT)
        }
        val legacyDensity = prefs.getString(LEGACY_DENSITY_KEY, null)
            ?.let { name -> runCatching { GalleryDensity.valueOf(name) }.getOrNull() }
            ?: return DEFAULT
        return GalleryDisplayMode.fromDensity(legacyDensity)
    }

    /** Persiste le mode et maintient l'ancienne clé pour une éventuelle rétrogradation. */
    fun setDisplayMode(mode: GalleryDisplayMode) {
        prefs.edit()
            .putString(MODE_KEY, mode.name)
            .apply {
                mode.density?.let { putString(LEGACY_DENSITY_KEY, it.name) }
            }
            .apply()
    }

    private companion object {
        const val PREFS = "florapin_gallery"
        const val MODE_KEY = "display_mode"
        const val LEGACY_DENSITY_KEY = "grid_density"
        val DEFAULT = GalleryDisplayMode.COMFORTABLE
    }
}
