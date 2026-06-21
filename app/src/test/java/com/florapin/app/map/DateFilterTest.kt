package com.florapin.app.map

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun thisYear_isStartOfCurrentYear() {
        val threshold = DateFilter.THIS_YEAR.minTimestamp(now)!!
        assertTrue(threshold <= now)

        val calendar = Calendar.getInstance().apply { timeInMillis = threshold }
        assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH))
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
    }
}
