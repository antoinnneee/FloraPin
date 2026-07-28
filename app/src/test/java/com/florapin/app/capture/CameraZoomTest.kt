package com.florapin.app.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraZoomTest {
    @Test
    fun `la position x1 suit la courbe CameraX avec un ultra grand angle`() {
        assertEquals(
            0.5263f,
            linearZoomForRatio(ratio = 1f, minZoom = 0.5f, maxZoom = 10f),
            0.0001f,
        )
    }

    @Test
    fun `le raccourci x10 atteint la fin sur un capteur limite a x10`() {
        assertEquals(
            1f,
            linearZoomForRatio(ratio = 10f, minZoom = 0.5f, maxZoom = 10f),
            0.0001f,
        )
    }

    @Test
    fun `le cran x1 attire seulement les valeurs proches`() {
        val oneX = linearZoomForRatio(ratio = 1f, minZoom = 0.5f, maxZoom = 10f)

        assertEquals(
            oneX,
            snapLinearZoomToStops(oneX + 0.02f, minZoom = 0.5f, maxZoom = 10f),
            0.0001f,
        )
        assertEquals(
            oneX + 0.04f,
            snapLinearZoomToStops(oneX + 0.04f, minZoom = 0.5f, maxZoom = 10f),
            0.0001f,
        )
    }

    @Test
    fun `les crans x2 x3 et x5 restent plus discrets que x1`() {
        listOf(2f, 3f, 5f).forEach { ratio ->
            val stop = linearZoomForRatio(ratio, minZoom = 0.5f, maxZoom = 10f)
            assertEquals(
                stop,
                snapLinearZoomToStops(stop + 0.01f, minZoom = 0.5f, maxZoom = 10f),
                0.0001f,
            )
            assertEquals(
                stop + 0.02f,
                snapLinearZoomToStops(stop + 0.02f, minZoom = 0.5f, maxZoom = 10f),
                0.0001f,
            )
        }
    }
}
