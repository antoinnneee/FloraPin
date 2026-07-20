package com.florapin.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BloomDownloadIndicatorTest {
    @Test
    fun `progression maps from closed bud to open flower`() {
        assertEquals(0, bloomFrameIndex(0f))
        assertEquals(4, bloomFrameIndex(0.5f))
        assertEquals(7, bloomFrameIndex(1f))
    }

    @Test
    fun `progression is clamped to available frames`() {
        assertEquals(0, bloomFrameIndex(-1f))
        assertEquals(7, bloomFrameIndex(2f))
    }
}
