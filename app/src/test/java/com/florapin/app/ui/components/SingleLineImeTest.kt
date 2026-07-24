package com.florapin.app.ui.components

import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import org.junit.Assert.assertEquals
import org.junit.Test

class SingleLineImeTest {

    @Test
    fun `single line fields expose a done action`() {
        val options = singleLineKeyboardOptions()

        assertEquals(ImeAction.Done, options.imeAction)
    }

    @Test
    fun `single line fields preserve their keyboard type`() {
        val options = singleLineKeyboardOptions(KeyboardType.Email)

        assertEquals(KeyboardType.Email, options.keyboardType)
        assertEquals(ImeAction.Done, options.imeAction)
    }
}
