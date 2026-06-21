package com.florapin.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun emptyList_roundTripsToEmpty() {
        val raw = converters.fromTags(emptyList())
        assertEquals(emptyList<String>(), converters.toTags(raw))
    }

    @Test
    fun tags_roundTrip() {
        val tags = listOf("rose", "jardin", "été")
        val raw = converters.fromTags(tags)
        assertEquals(tags, converters.toTags(raw))
    }
}
