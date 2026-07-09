package com.florapin.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapIconScaleTest {

    @Test
    fun minSeparation_isNullBelowTwoPoints() {
        assertNull(minSeparationPx(emptyList()))
        assertNull(minSeparationPx(listOf(ScreenPoint(10f, 10f))))
    }

    @Test
    fun minSeparation_findsClosestPairRegardlessOfOrder() {
        val points = listOf(
            ScreenPoint(0f, 0f),
            ScreenPoint(300f, 400f), // à 500 du premier
            ScreenPoint(303f, 404f), // à 5 du deuxième
            ScreenPoint(900f, 0f),
        )
        assertEquals(5f, minSeparationPx(points)!!, 0.001f)
    }

    @Test
    fun scale_growsWithZoom() {
        val near = photoIconScale(zoom = PHOTO_ICON_MIN_ZOOM.toDouble(), minSeparationPx = null)
        val nearer = photoIconScale(zoom = PHOTO_ICON_MIN_ZOOM + 1.0, minSeparationPx = null)
        assertEquals(PHOTO_ICON_SCALE_INITIAL, near, 0.001f)
        assertTrue("$nearer devrait dépasser $near", nearer > near)
    }

    @Test
    fun scale_isCappedSoOverlapStaysUnderTenPercent() {
        // Voisines distantes de 200 px : un diamètre de 222 px les fait se
        // recouvrir d'exactement 10 %.
        val separation = 200f
        val scale = photoIconScale(zoom = 21.0, minSeparationPx = separation)
        val diameter = scale * PHOTO_ICON_SIZE_PX
        val overlap = (diameter - separation) / diameter
        assertTrue("chevauchement de $overlap", overlap <= 0.1001f)
    }

    @Test
    fun scale_ignoresNeighboursWhenTheyAreFarApart() {
        val unconstrained = photoIconScale(zoom = 17.0, minSeparationPx = null)
        val roomy = photoIconScale(zoom = 17.0, minSeparationPx = 2000f)
        assertEquals(unconstrained, roomy, 0.001f)
    }

    @Test
    fun scale_keepsPastillesVisibleOnVeryDenseClusters() {
        // Fleurs superposées : mieux vaut un peu de chevauchement que l'invisible.
        val scale = photoIconScale(zoom = 18.0, minSeparationPx = 1f)
        assertTrue("$scale devrait rester perceptible", scale >= 0.3f)
    }

    @Test
    fun scaleChange_isIgnoredWhenImperceptible() {
        assertTrue(isScaleChangeVisible(0.70f, 0.75f))
        assertTrue(!isScaleChangeVisible(0.70f, 0.71f))
    }
}
