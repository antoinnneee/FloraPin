package com.florapin.app.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryDensityTest {

    @Test
    fun `phone portrait densities use visibly different column counts`() {
        assertEquals(3, GalleryDensity.COMPACT.phonePortraitColumns)
        assertEquals(2, GalleryDensity.COMFORTABLE.phonePortraitColumns)
        assertEquals(1, GalleryDensity.LARGE.phonePortraitColumns)
    }

    @Test
    fun `display selector exposes list and the three grid densities`() {
        assertEquals(
            listOf("Liste", "Compact", "Confort", "Grande"),
            GalleryDisplayMode.entries.map { it.label },
        )
        assertEquals(null, GalleryDisplayMode.LIST.density)
        assertEquals(GalleryDensity.COMPACT, GalleryDisplayMode.COMPACT.density)
        assertEquals(GalleryDensity.COMFORTABLE, GalleryDisplayMode.COMFORTABLE.density)
        assertEquals(GalleryDensity.LARGE, GalleryDisplayMode.LARGE.density)
    }

    @Test
    fun `legacy grid density maps to the matching display mode`() {
        GalleryDensity.entries.forEach { density ->
            assertEquals(
                density,
                GalleryDisplayMode.fromDensity(density).density,
            )
        }
    }
}
