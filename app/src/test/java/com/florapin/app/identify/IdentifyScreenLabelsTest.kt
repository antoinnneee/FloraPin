package com.florapin.app.identify

import org.junit.Assert.assertEquals
import org.junit.Test

class IdentifyScreenLabelsTest {

    @Test
    fun commentLabelShowsCountOnlyWhenCommentsExist() {
        assertEquals("💬 Discuter", commentActionLabel(0))
        assertEquals("💬 Discuter · 1 commentaire", commentActionLabel(1))
        assertEquals("💬 Discuter · 4 commentaires", commentActionLabel(4))
    }
}
