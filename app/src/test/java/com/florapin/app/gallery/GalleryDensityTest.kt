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
}
