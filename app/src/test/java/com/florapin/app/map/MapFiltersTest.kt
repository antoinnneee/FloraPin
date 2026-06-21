package com.florapin.app.map

import com.florapin.app.data.FlowerEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class MapFiltersTest {

    private val now = 10_000_000_000L

    private fun flower(
        id: Long,
        species: String? = null,
        ownerId: String? = null,
        createdAt: Long = now,
        lat: Double? = 48.0,
        lng: Double? = 2.0,
    ) = FlowerEntity(
        id = id,
        imagePath = "/p$id.jpg",
        latitude = lat,
        longitude = lng,
        createdAt = createdAt,
        species = species,
        ownerId = ownerId,
    )

    @Test
    fun availableSpecies_distinctSortedNonBlank() {
        val flowers = listOf(
            flower(1, species = "Rosa"),
            flower(2, species = "Tulipa"),
            flower(3, species = "Rosa"),
            flower(4, species = null),
            flower(5, species = ""),
        )
        assertEquals(listOf("Rosa", "Tulipa"), flowers.availableSpecies())
    }

    @Test
    fun noFilters_keepsAllGeolocated() {
        val flowers = listOf(
            flower(1),
            flower(2, lat = null), // sans position -> ignorée
        )
        val markers = flowers.toFilteredMarkers(
            dateFilter = DateFilter.ALL,
            species = null,
            friendsOnly = false,
            currentUserId = "me",
            now = now,
        )
        assertEquals(listOf(1L), markers.map { it.id })
    }

    @Test
    fun speciesFilter_keepsOnlyMatching() {
        val flowers = listOf(
            flower(1, species = "Rosa"),
            flower(2, species = "Tulipa"),
        )
        val markers = flowers.toFilteredMarkers(
            dateFilter = DateFilter.ALL,
            species = "Rosa",
            friendsOnly = false,
            currentUserId = "me",
            now = now,
        )
        assertEquals(listOf(1L), markers.map { it.id })
    }

    @Test
    fun friendsOnly_excludesMineAndUnsynced() {
        val flowers = listOf(
            flower(1, ownerId = "me"),     // à moi -> exclue
            flower(2, ownerId = "alice"),  // ami -> gardée
            flower(3, ownerId = null),     // capture locale non synchronisée -> exclue
        )
        val markers = flowers.toFilteredMarkers(
            dateFilter = DateFilter.ALL,
            species = null,
            friendsOnly = true,
            currentUserId = "me",
            now = now,
        )
        assertEquals(listOf(2L), markers.map { it.id })
    }

    @Test
    fun dateFilter_appliesThreshold() {
        val dayMs = 24L * 60 * 60 * 1000
        val flowers = listOf(
            flower(1, createdAt = now),               // récente
            flower(2, createdAt = now - dayMs * 10),  // > 7 jours
        )
        val markers = flowers.toFilteredMarkers(
            dateFilter = DateFilter.LAST_7_DAYS,
            species = null,
            friendsOnly = false,
            currentUserId = "me",
            now = now,
        )
        assertEquals(listOf(1L), markers.map { it.id })
    }
}
