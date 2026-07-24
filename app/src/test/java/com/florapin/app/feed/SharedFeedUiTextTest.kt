package com.florapin.app.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedFeedUiTextTest {

    @Test
    fun `comment button includes the current count`() {
        assertEquals("💬 Commenter (3)", commentButtonLabel(3))
        assertEquals("💬 Commenter (0)", commentButtonLabel(0))
    }

    @Test
    fun `comment button never displays a negative count`() {
        assertEquals("💬 Commenter (0)", commentButtonLabel(-1))
    }
}
