package com.florapin.app.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeUiStateTest {

    @Test
    fun `a badge can be in progress before its first star`() {
        val state = badge(currentValue = 3)

        assertTrue(state.started)
        assertFalse(state.unlocked)
        assertFalse(state.maxed)
    }

    @Test
    fun `a badge at zero is not started`() {
        assertFalse(badge(currentValue = 0).started)
    }

    @Test
    fun `an unavailable badge never appears in progress`() {
        assertFalse(badge(currentValue = 3, available = false).started)
    }

    @Test
    fun `the outline becomes stronger only after the first star`() {
        assertEquals(1.dp, badgeOutlineWidth(badge(currentValue = 3)))
        assertEquals(2.dp, badgeOutlineWidth(badge(currentValue = 10)))
        assertEquals(1.dp, badgeOutlineWidth(badge(currentValue = 10, available = false)))
    }

    private fun badge(
        currentValue: Int,
        available: Boolean = true,
    ) = BadgeUiState(
        id = "herbier",
        emoji = "🌿",
        title = "Herbier",
        tiers = listOf(10, 50, 100),
        currentValue = currentValue,
        available = available,
    )
}
