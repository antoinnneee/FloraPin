package com.florapin.app.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapStyleTest {

    @Test
    fun selectorOnlyExposesSupportedBaseStyles() {
        assertEquals(
            listOf("Clair", "Satellite", "Hybride", "Hiver"),
            MapStyle.entries.map { it.label },
        )
    }

    @Test
    fun removedPreferenceFallsBackToLightStyle() {
        assertEquals(MapStyle.BRIGHT, MapStyle.fromId("streets-v2"))
        assertEquals(MapStyle.BRIGHT, MapStyle.fromId("dataviz"))
    }
}
