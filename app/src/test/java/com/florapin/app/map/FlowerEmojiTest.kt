package com.florapin.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowerEmojiTest {

    @Test
    fun nullOrBlank_returnsDefault() {
        assertEquals(FlowerEmoji.DEFAULT, FlowerEmoji.forSpecies(null))
        assertEquals(FlowerEmoji.DEFAULT, FlowerEmoji.forSpecies(""))
        assertEquals(FlowerEmoji.DEFAULT, FlowerEmoji.forSpecies("   "))
    }

    @Test
    fun unknownSpecies_returnsDefault() {
        assertEquals(FlowerEmoji.DEFAULT, FlowerEmoji.forSpecies("Orchidée bizarre"))
    }

    @Test
    fun knownSpecies_caseInsensitive_latinOrVernacular() {
        assertEquals("🌻", FlowerEmoji.forSpecies("Tournesol"))
        assertEquals("🌻", FlowerEmoji.forSpecies("Helianthus annuus"))
        assertEquals("🌹", FlowerEmoji.forSpecies("rosa gallica"))
        assertEquals("🌹", FlowerEmoji.forSpecies("Un beau ROSIER"))
        assertEquals("🌷", FlowerEmoji.forSpecies("Tulipe rouge"))
    }

    @Test
    fun all_containsDefaultAndIsDistinct() {
        assertTrue(FlowerEmoji.all.contains(FlowerEmoji.DEFAULT))
        assertEquals(FlowerEmoji.all.distinct(), FlowerEmoji.all)
    }
}
