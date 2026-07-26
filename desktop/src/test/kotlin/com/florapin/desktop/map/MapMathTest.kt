package com.florapin.desktop.map

import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * La projection est le socle de la carte : marqueurs, tuiles et zoom au
 * curseur en dépendent tous. Une erreur ici décale silencieusement les photos
 * de leur emplacement réel, ce qu'aucun test d'interface ne rattraperait.
 */
class MapMathTest {

    @Test
    fun `l'aller-retour longitude conserve la valeur`() {
        listOf(-180.0, -73.5, 0.0, 2.3522, 139.7, 179.9).forEach { lon ->
            val x = MapMath.lonToWorldX(lon, zoom = 12)
            assertEquals(lon, MapMath.worldXToLon(x, 12), 1e-9, "longitude $lon")
        }
    }

    @Test
    fun `l'aller-retour latitude conserve la valeur`() {
        listOf(-84.0, -33.9, 0.0, 48.8566, 71.0, 85.0).forEach { lat ->
            val y = MapMath.latToWorldY(lat, zoom = 12)
            assertEquals(lat, MapMath.worldYToLat(y, 12), 1e-7, "latitude $lat")
        }
    }

    @Test
    fun `le meridien et l'equateur tombent au centre du monde`() {
        val zoom = 4
        val size = MapMath.worldSize(zoom)
        assertEquals(size / 2, MapMath.lonToWorldX(0.0, zoom), 1e-6)
        assertEquals(size / 2, MapMath.latToWorldY(0.0, zoom), 1e-6)
    }

    @Test
    fun `les coins du monde correspondent aux limites de Mercator`() {
        val zoom = 3
        val size = MapMath.worldSize(zoom)
        assertEquals(0.0, MapMath.lonToWorldX(-180.0, zoom), 1e-6)
        assertEquals(size, MapMath.lonToWorldX(180.0, zoom), 1e-6)
        assertEquals(0.0, MapMath.latToWorldY(MapMath.MAX_LATITUDE, zoom), 1e-3)
        assertEquals(size, MapMath.latToWorldY(-MapMath.MAX_LATITUDE, zoom), 1e-3)
    }

    @Test
    fun `les latitudes hors limites sont ramenees dans le monde visible`() {
        val zoom = 5
        val size = MapMath.worldSize(zoom)
        // Le pôle exact fait diverger Mercator : sans bornage, la tuile
        // calculée partirait à l'infini.
        val north = MapMath.latToWorldY(90.0, zoom)
        val south = MapMath.latToWorldY(-90.0, zoom)
        assertTrue(north >= 0.0 && north <= size, "nord borné : $north")
        assertTrue(south >= 0.0 && south <= size, "sud borné : $south")
    }

    @Test
    fun `doubler le zoom double la taille du monde`() {
        assertEquals(MapMath.worldSize(6) * 2, MapMath.worldSize(7), 1e-6)
    }

    @Test
    fun `zoomToFit cadre une ville sans deborder de la fenetre`() {
        // Emprise parisienne, dans une fenêtre de taille courante.
        val zoom = MapMath.zoomToFit(
            minLat = 48.815, maxLat = 48.902,
            minLon = 2.224, maxLon = 2.469,
            widthPx = 1200.0, heightPx = 800.0,
        )
        val width = abs(MapMath.lonToWorldX(2.469, zoom) - MapMath.lonToWorldX(2.224, zoom))
        val height = abs(MapMath.latToWorldY(48.902, zoom) - MapMath.latToWorldY(48.815, zoom))

        assertTrue(width <= 1200.0, "largeur occupée $width")
        assertTrue(height <= 800.0, "hauteur occupée $height")
        // Un cadrage correct doit aussi remplir l'écran : le niveau suivant
        // déborderait, preuve qu'on ne dézoome pas inutilement.
        val nextWidth = abs(
            MapMath.lonToWorldX(2.469, zoom + 1) - MapMath.lonToWorldX(2.224, zoom + 1),
        )
        val nextHeight = abs(
            MapMath.latToWorldY(48.902, zoom + 1) - MapMath.latToWorldY(48.815, zoom + 1),
        )
        assertTrue(nextWidth > 1200.0 || nextHeight > 800.0, "cadrage trop large")
    }

    @Test
    fun `zoomToFit reste dans les bornes pour une emprise mondiale`() {
        val zoom = MapMath.zoomToFit(-80.0, 80.0, -170.0, 170.0, 900.0, 600.0)
        assertTrue(zoom in 1..16, "zoom mondial $zoom")
    }

    @Test
    fun `zoomToFit sur une fenetre nulle renvoie un zoom exploitable`() {
        // Cas réel : la carte est mesurée avant sa première mise en page.
        assertEquals(2, MapMath.zoomToFit(48.0, 49.0, 2.0, 3.0, 0.0, 0.0))
    }
}
