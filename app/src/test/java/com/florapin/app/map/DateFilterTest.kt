package com.florapin.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateFilterTest {

    private val now = 1_700_000_000_000L // instant de référence arbitraire

    @Test
    fun all_hasNoThreshold() {
        assertNull(DateFilter.ALL.minTimestamp(now))
    }

    @Test
    fun last7Days_isSevenDaysBefore() {
        val expected = now - 7L * 24 * 60 * 60 * 1000
        assertEquals(expected, DateFilter.LAST_7_DAYS.minTimestamp(now))
    }

    @Test
    fun last30Days_isThirtyDaysBefore() {
        val expected = now - 30L * 24 * 60 * 60 * 1000
        assertEquals(expected, DateFilter.LAST_30_DAYS.minTimestamp(now))
    }

    @Test
    fun selector_hasExactlyThreeCompactChoices() {
        assertEquals(listOf("Toutes", "7 jours", "30 jours"), DateFilter.entries.map { it.label })
    }
}
