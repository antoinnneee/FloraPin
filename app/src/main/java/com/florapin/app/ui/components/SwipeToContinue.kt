package com.florapin.app.ui.components

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Calls [onSwipe] after a sufficiently long swipe to the left. */
fun Modifier.swipeToContinue(
    enabled: Boolean = true,
    onSwipe: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    pointerInput(onSwipe) {
        var dragged = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragged = 0f },
            onHorizontalDrag = { _, amount -> dragged += amount },
            onDragEnd = {
                if (dragged <= -size.width * SWIPE_THRESHOLD_FRACTION) onSwipe()
                dragged = 0f
            },
            onDragCancel = { dragged = 0f },
        )
    }
}

private const val SWIPE_THRESHOLD_FRACTION = 0.20f
