package com.florapin.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.geometry.LatLngBounds

class OfflineMapMergeTest {
    @Test
    fun `une zone deja couverte niveaux de zoom compris est reconnue`() {
        val region = region(
            id = 1,
            bounds = bounds(north = 49.0, east = 3.0, south = 48.0, west = 2.0),
            minimumZoom = 12.0,
            maximumZoom = 18.0,
        )

        assertTrue(
            region.covers(
                bounds = bounds(north = 48.8, east = 2.8, south = 48.2, west = 2.2),
                minimumZoom = 13.0,
                maximumZoom = 18.0,
            ),
        )
        assertFalse(
            region.covers(
                bounds = bounds(north = 48.8, east = 2.8, south = 48.2, west = 2.2),
                minimumZoom = 13.0,
                maximumZoom = 19.0,
            ),
        )
    }

    @Test
    fun `les recouvrements en chaine sont regroupes dans une seule zone`() {
        val first = region(
            id = 1,
            bounds = bounds(north = 49.0, east = 3.0, south = 48.0, west = 2.0),
        )
        val second = region(
            id = 2,
            bounds = bounds(north = 49.0, east = 3.8, south = 48.0, west = 2.8),
        )
        val separate = region(
            id = 3,
            bounds = bounds(north = 46.0, east = 7.0, south = 45.0, west = 6.0),
        )

        val result = findOverlappingRegionCluster(
            selection = bounds(north = 48.8, east = 2.2, south = 48.2, west = 1.5),
            styleId = "bright-v2",
            regions = listOf(first, second, separate),
        )

        assertEquals(listOf(1L, 2L), result.map { it.id })
    }

    @Test
    fun `une zone d'un autre style ne fusionne pas`() {
        val satellite = region(
            id = 1,
            styleId = "satellite",
            bounds = bounds(north = 49.0, east = 3.0, south = 48.0, west = 2.0),
        )

        assertTrue(
            findOverlappingRegionCluster(
                selection = bounds(north = 48.8, east = 2.8, south = 48.2, west = 2.2),
                styleId = "bright-v2",
                regions = listOf(satellite),
            ).isEmpty(),
        )
    }

    private fun region(
        id: Long,
        bounds: LatLngBounds,
        styleId: String = "bright-v2",
        minimumZoom: Double = 12.0,
        maximumZoom: Double = 18.0,
    ) = OfflineMapRegionUi(
        id = id,
        name = "Zone $id",
        styleId = styleId,
        progress = 1f,
        completedBytes = 1_000L,
        isComplete = true,
        isActive = false,
        createdAt = id,
        bounds = bounds,
        minimumZoom = minimumZoom,
        maximumZoom = maximumZoom,
        pixelRatio = 2f,
    )

    private fun bounds(
        north: Double,
        east: Double,
        south: Double,
        west: Double,
    ) = LatLngBounds.from(north, east, south, west)
}
