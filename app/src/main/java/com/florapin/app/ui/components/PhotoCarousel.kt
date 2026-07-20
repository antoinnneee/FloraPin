package com.florapin.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * Carrousel d'images horizontal (un « page » par photo). Tape pour ouvrir la
 * visionneuse plein écran avec zoom. Réutilisé partout où une fleur peut avoir
 * plusieurs photos : feed d'amis, écran « à identifier », détail.
 *
 * @param previewModels modèles légers affichés dans le carrousel (miniatures).
 * @param fullModels modèles pleine résolution ouverts en plein écran (par défaut
 *   identiques à [previewModels]).
 */
@Composable
fun PhotoCarousel(
    previewModels: List<Any?>,
    modifier: Modifier = Modifier,
    fullModels: List<Any?> = previewModels,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    if (previewModels.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { previewModels.size })
    var viewerStart by remember { mutableStateOf<Int?>(null) }

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            AsyncImage(
                model = previewModels[page],
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { viewerStart = page },
            )
        }
        // Compteur « 2 / 5 » quand il y a plusieurs photos. En haut à droite : le
        // bas de l'image est occupé par les actions du feed (réaction, sélection).
        if (previewModels.size > 1) {
            Surface(
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${previewModels.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }

    viewerStart?.let { start ->
        FullscreenPhotoViewer(
            models = fullModels,
            startIndex = start,
            onDismiss = { viewerStart = null },
        )
    }
}

/**
 * Visionneuse plein écran : swipe horizontal entre les photos et pincement pour
 * zoomer (double-tap pour (dé)zoomer). Le swipe inter-photos est désactivé tant
 * qu'une image est zoomée, pour que le déplacement panoramique ne change pas de
 * page.
 */
@Composable
fun FullscreenPhotoViewer(
    models: List<Any?>,
    startIndex: Int,
    onDismiss: () -> Unit,
    detailsContent: (@Composable () -> Unit)? = null,
) {
    if (models.isEmpty()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, models.size - 1),
            pageCount = { models.size },
        )
        var zoomed by remember { mutableStateOf(false) }
        var showDetails by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (showDetails && detailsContent != null) {
                detailsContent()
            } else {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !zoomed,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    ZoomableImage(
                        model = models[page],
                        onZoomChange = { zoomed = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!showDetails && models.size > 1) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${models.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                if (detailsContent != null) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = CircleShape,
                        onClick = { showDetails = !showDetails },
                    ) {
                        Text(
                            text = if (showDetails) "Photo" else "Details",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = CircleShape,
                    onClick = onDismiss,
                    // Cible tactile ≥ 48 dp (TÂCHE 6.18, accessibilité).
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Glyphe « ✕ » lu littéralement par TalkBack : libellé parlant
                        // et masquage du caractère de l'arbre d'accessibilité.
                        EmojiIcon(
                            emoji = "✕",
                            contentDescription = "Fermer",
                            style = TextStyle(color = Color.White),
                        )
                    }
                }
            }
        }
    }
}

/** Image zoomable par pincement + double-tap, avec déplacement quand zoomée. */
@Composable
private fun ZoomableImage(
    model: Any?,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    fun reset() {
        scale = 1f; offsetX = 0f; offsetY = 0f
        onZoomChange(false)
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            reset()
                        } else {
                            scale = 2.5f
                            onZoomChange(true)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                // Gestes de zoom/déplacement gérés à la main (plutôt que
                // detectTransformGestures) pour NE PAS consommer un simple
                // glissement à un doigt tant que l'image n'est pas zoomée : ainsi
                // le HorizontalPager parent reçoit le swipe inter-photos. On ne
                // capture le geste que s'il y a ≥ 2 doigts (pincement) ou si
                // l'image est déjà zoomée (déplacement panoramique).
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } >= 2
                        if (multiTouch || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                                onZoomChange(true)
                            } else {
                                offsetX = 0f; offsetY = 0f
                                onZoomChange(false)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY,
                ),
        )
    }
}
