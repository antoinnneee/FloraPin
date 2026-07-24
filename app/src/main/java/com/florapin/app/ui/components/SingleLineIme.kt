package com.florapin.app.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.florapin.app.util.Haptics

/**
 * Configuration commune des champs mono-ligne : la touche Entrée est présentée
 * comme une validation, puis retire le focus, ferme le clavier et confirme
 * discrètement la prise en compte par un retour haptique.
 */
fun singleLineKeyboardOptions(
    keyboardType: KeyboardType = KeyboardType.Text,
): KeyboardOptions = KeyboardOptions(
    keyboardType = keyboardType,
    imeAction = ImeAction.Done,
)

@Composable
fun rememberSingleLineKeyboardActions(): KeyboardActions {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    return remember(focusManager, keyboardController, haptic) {
        KeyboardActions(
            onDone = {
                focusManager.clearFocus()
                keyboardController?.hide()
                Haptics.tap(haptic)
            },
        )
    }
}
