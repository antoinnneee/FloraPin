package com.florapin.app.gallery

import com.florapin.app.data.FlowerEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GallerySearchSortTest {

    private fun flower(
        id: Long,
        species: String? = null,
        notes: String = "",
        tags: List<String> = emptyList(),
        createdAt: Long = 0,
    ) = FlowerEntity(
        id = id,
        imagePath = "/p$id.jpg",
        createdAt = createdAt,
        species = species,
        notes = notes,
        tags = tags,
    )

    private val flowers = listOf(
        flower(1, species = "Rosa", notes = "jardin", tags = listOf("rouge"), createdAt = 300),
        flower(2, species = "Tulipa", notes = "balcon", createdAt = 100),
        flower(3, species = null, notes = "Belle ROSE sauvage", createdAt = 200),
    )

    @Test
    fun filter_emptyQuery_returnsAll() {
        assertEquals(listOf(1L, 2L, 3L), flowers.filterByQuery("  ").map { it.id })
    }

    @Test
    fun filter_matchesSpeciesNotesTags_caseInsensitive() {
        // "ros" : espèce "Rosa" (1) + notes "Belle ROSE" (3)
        assertEquals(listOf(1L, 3L), flowers.filterByQuery("ROS").map { it.id })
        // par étiquette
        assertEquals(listOf(1L), flowers.filterByQuery("ROUGE").map { it.id })
        // par notes
        assertEquals(listOf(2L), flowers.filterByQuery("balcon").map { it.id })
    }

    @Test
    fun filter_noMatch_returnsEmpty() {
        assertEquals(emptyList<Long>(), flowers.filterByQuery("xyz").map { it.id })
    }

    @Test
    fun sort_dateDescIsDefaultOrder() {
        assertEquals(
            listOf(1L, 3L, 2L),
            flowers.sortedByOrder(GallerySort.DATE_DESC).map { it.id },
        )
    }

    @Test
    fun sort_dateAsc() {
        assertEquals(
            listOf(2L, 3L, 1L),
            flowers.sortedByOrder(GallerySort.DATE_ASC).map { it.id },
        )
    }

    @Test
    fun sort_bySpecies_nullsLast() {
        // Rosa (1), Tulipa (2), puis l'espèce nulle (3) en dernier.
        assertEquals(
            listOf(1L, 2L, 3L),
            flowers.sortedByOrder(GallerySort.SPECIES).map { it.id },
        )
    }
}
