package com.florapin.app.gallery

import com.florapin.app.data.FlowerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/** Vérifie le regroupement de la galerie par mois de capture (TÂCHE 6.7). */
class GalleryMonthGroupingTest {

    /** epoch millis pour un (année, mois 1-12, jour) à minuit, fuseau par défaut. */
    private fun at(year: Int, month: Int, day: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.clear()
        cal.set(year, month - 1, day, 12, 0, 0)
        return cal.timeInMillis
    }

    private fun flower(id: Long, createdAt: Long) =
        FlowerEntity(id = id, imagePath = "/p$id.jpg", createdAt = createdAt)

    @Test
    fun grouped_insertsHeaderPerMonth_inOrder() {
        // Deux fleurs en juin 2026, une en mai 2026 (déjà triées desc par date).
        val flowers = listOf(
            flower(1, at(2026, 6, 20)),
            flower(2, at(2026, 6, 3)),
            flower(3, at(2026, 5, 28)),
        )
        val rows = flowers.groupedByMonth()

        // En-tête juin, 2 fleurs, en-tête mai, 1 fleur.
        assertEquals(5, rows.size)
        assertTrue(rows[0] is GalleryRow.MonthHeader)
        assertEquals("2026-06", rows[0].monthKey)
        assertEquals(1L, (rows[1] as GalleryRow.Flower).flower.id)
        assertEquals(2L, (rows[2] as GalleryRow.Flower).flower.id)
        assertTrue(rows[3] is GalleryRow.MonthHeader)
        assertEquals("2026-05", rows[3].monthKey)
        assertEquals(3L, (rows[4] as GalleryRow.Flower).flower.id)
    }

    @Test
    fun grouped_singleMonth_oneHeader() {
        val flowers = listOf(
            flower(1, at(2026, 6, 20)),
            flower(2, at(2026, 6, 1)),
        )
        val rows = flowers.groupedByMonth()
        assertEquals(1, rows.count { it is GalleryRow.MonthHeader })
        assertEquals(2, rows.count { it is GalleryRow.Flower })
    }

    @Test
    fun grouped_empty_returnsEmpty() {
        assertTrue(emptyList<FlowerEntity>().groupedByMonth().isEmpty())
    }

    @Test
    fun grouped_usesCaptureDate_notReorderedByOtherFields() {
        // L'ordre d'entrée est respecté tel quel (le tri par date est fait en amont).
        val flowers = listOf(
            flower(1, at(2026, 7, 5)),
            flower(2, at(2026, 6, 30)),
            flower(3, at(2026, 6, 15)),
        )
        val rows = flowers.groupedByMonth()
        val keys = rows.map { it.monthKey }
        assertEquals(listOf("2026-07", "2026-07", "2026-06", "2026-06", "2026-06"), keys)
    }
}
