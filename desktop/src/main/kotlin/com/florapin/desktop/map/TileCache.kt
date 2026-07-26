package com.florapin.desktop.map

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import com.florapin.desktop.core.DesktopConfig
import com.florapin.desktop.core.ImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Images de la carte (tuiles et vignettes de marqueurs) exposées de façon
 * **observable**.
 *
 * Le rendu de la carte est un `Canvas` : il lui faut une valeur immédiate à
 * chaque passe de dessin, pas une coroutine. On renvoie donc `null` tant que
 * l'image n'est pas là, et l'arrivée d'une image met à jour un état Compose,
 * ce qui redéclenche le dessin. Sans cet état observable, une tuile téléchargée
 * ne s'afficherait qu'au prochain déplacement de la carte.
 */
class TileCache {

    private val images = mutableStateMapOf<String, ImageBitmap>()

    /**
     * Dernière tentative par URL. Deux rôles : ne pas relancer en boucle un
     * téléchargement déjà en cours (le Canvas redessine plusieurs fois par
     * seconde), et laisser une seconde chance après une coupure réseau plutôt
     * que de laisser un trou gris définitif.
     */
    private val attempts = mutableMapOf<String, Long>()

    fun image(url: String, scope: CoroutineScope): ImageBitmap? {
        images[url]?.let { return it }
        val now = System.currentTimeMillis()
        val last = attempts[url]
        if (last != null && now - last < RETRY_DELAY_MS) return null
        attempts[url] = now
        scope.launch {
            ImageStore.load(url)?.let { images[url] = it }
        }
        return null
    }

    fun tile(
        zoom: Int,
        x: Int,
        y: Int,
        style: DesktopMapStyle,
        scope: CoroutineScope,
    ): ImageBitmap? = image(tileUrl(zoom, x, y, style), scope)

    private companion object {
        const val RETRY_DELAY_MS = 15_000L

        /**
         * Endpoint raster de MapTiler. L'app Android consomme les mêmes styles
         * en vectoriel via MapLibre ; le rendu diffère à la marge, mais la
         * carte reste reconnaissable d'un client à l'autre.
         */
        fun tileUrl(zoom: Int, x: Int, y: Int, style: DesktopMapStyle): String =
            "https://api.maptiler.com/maps/${style.id}/256/$zoom/$x/$y.${style.extension}" +
                "?key=${DesktopConfig.maptilerApiKey}"
    }
}
