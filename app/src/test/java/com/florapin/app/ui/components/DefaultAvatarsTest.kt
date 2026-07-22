package com.florapin.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAvatarsTest {
    @Test
    fun `le catalogue contient onze avatars uniques`() {
        assertEquals(11, DefaultAvatars.all.size)
        assertEquals(11, DefaultAvatars.all.map { it.id }.distinct().size)
        assertEquals(11, DefaultAvatars.all.map { it.resourceId }.distinct().size)
    }

    @Test
    fun `un meme utilisateur conserve le meme avatar`() {
        val first = DefaultAvatars.assignedTo("user-123")
        val second = DefaultAvatars.assignedTo("user-123")

        assertEquals(first, second)
        assertTrue(first in DefaultAvatars.all)
    }
}
