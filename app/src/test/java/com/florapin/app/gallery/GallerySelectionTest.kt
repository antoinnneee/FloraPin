package com.florapin.app.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

/** Bascule de la sélection multiple de la galerie (TÂCHE 6.6). */
class GallerySelectionTest {

    @Test
    fun toggled_addsWhenAbsent() {
        assertEquals(setOf(1L), emptySet<Long>().toggled(1))
        assertEquals(setOf(1L, 2L), setOf(1L).toggled(2))
    }

    @Test
    fun toggled_removesWhenPresent() {
        assertEquals(emptySet<Long>(), setOf(1L).toggled(1))
        assertEquals(setOf(2L), setOf(1L, 2L).toggled(1))
    }

    @Test
    fun toggled_isInvolution() {
        val start = setOf(3L, 7L)
        assertEquals(start, start.toggled(5).toggled(5))
    }
}
