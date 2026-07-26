package com.florapin.desktop.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput

/** Modificateurs actifs au moment du clic — la grammaire de sélection Windows. */
data class ClickModifiers(val ctrl: Boolean, val shift: Boolean)

/**
 * Gestion complète d'un clic à la souris sur un élément de liste.
 *
 * Compose fournit `clickable`, mais il ignore le bouton droit et ne dit rien
 * des touches enfoncées : impossible d'en tirer un Ctrl+clic, un Maj+clic ou un
 * menu contextuel, c'est-à-dire l'essentiel de ce qu'un utilisateur de PC
 * attend d'une grille de photos. On descend donc au niveau des événements
 * pointeur pour tout traiter d'un seul tenant, y compris le double-clic, sans
 * empiler des détecteurs concurrents.
 *
 * Le menu contextuel se positionne seul sous le curseur (CursorDropdownMenu) :
 * l'appelant n'a donc pas à convertir de coordonnées.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.mouseInteractions(
    onClick: (ClickModifiers) -> Unit,
    onDoubleClick: () -> Unit = {},
    onSecondaryClick: () -> Unit = {},
): Modifier = pointerInput(Unit) {
    var lastClickAt = 0L
    awaitEachGesture {
        // Attente de l'appui initial, en mémorisant le bouton utilisé : sous
        // Windows le menu contextuel s'ouvre au relâchement du bouton droit.
        var secondary = false
        var pressedInWindow = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull()
            if (change != null && change.pressed) {
                secondary = event.buttons.isSecondaryPressed
                pressedInWindow = change.position
                break
            }
        }

        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (change.pressed) {
                if ((change.position - pressedInWindow).getDistance() > MOVE_TOLERANCE) moved = true
                continue
            }
            if (!moved) {
                if (secondary) {
                    onSecondaryClick()
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastClickAt < DOUBLE_CLICK_MS) {
                        lastClickAt = 0
                        onDoubleClick()
                    } else {
                        lastClickAt = now
                        onClick(
                            ClickModifiers(
                                ctrl = event.keyboardModifiers.isCtrlPressed,
                                shift = event.keyboardModifiers.isShiftPressed,
                            ),
                        )
                    }
                }
            }
            change.consume()
            break
        }
    }
}

/** Tolérance avant qu'un appui ne soit requalifié en glissement, en pixels. */
private const val MOVE_TOLERANCE = 5f
private const val DOUBLE_CLICK_MS = 400L
