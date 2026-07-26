package com.florapin.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.florapin.app.network.dto.fullPhotoUrls
import com.florapin.desktop.app.AppModel
import com.florapin.desktop.core.ImageStore
import androidx.compose.runtime.produceState

/**
 * Visionneuse plein écran.
 *
 * Les commandes sont celles d'une visionneuse Windows : flèches pour changer
 * de photo, molette pour zoomer, glisser pour se déplacer dans l'image,
 * double-clic pour revenir à l'ajustement automatique, Échap pour fermer. Le
 * zoom vise le curseur plutôt que le centre, seul comportement utilisable pour
 * inspecter un détail de pétale sans se perdre.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ViewerOverlay(
    model: AppModel,
    flowerId: String,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val ordered = model.visibleFlowers
    val flower = model.flowerById(flowerId) ?: run {
        onClose()
        return
    }
    val urls = remember(flower.id) { flower.fullPhotoUrls() }
    var photoIndex by remember(flower.id) { mutableStateOf(0) }
    var zoom by remember(flower.id, photoIndex) { mutableStateOf(1f) }
    var pan by remember(flower.id, photoIndex) { mutableStateOf(Offset.Zero) }
    val focusRequester = remember { FocusRequester() }

    val flowerIndex = ordered.indexOfFirst { it.id == flower.id }
    fun goTo(delta: Int) {
        // On parcourt d'abord les photos de la fiche courante, puis on passe à
        // la fiche voisine : l'utilisateur avance « photo par photo », sans
        // avoir à savoir comment elles sont regroupées.
        val next = photoIndex + delta
        when {
            next in urls.indices -> photoIndex = next
            flowerIndex < 0 -> Unit
            else -> {
                val target = ordered.getOrNull(flowerIndex + delta) ?: return
                onNavigate(target.id)
            }
        }
    }

    LaunchedEffect(flower.id) { runCatching { focusRequester.requestFocus() } }

    Surface(color = Color.Black.copy(alpha = 0.94f), modifier = Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionRight, Key.PageDown, Key.Spacebar -> {
                            goTo(1); true
                        }
                        Key.DirectionLeft, Key.PageUp -> {
                            goTo(-1); true
                        }
                        Key.Escape -> {
                            onClose(); true
                        }
                        else -> false
                    }
                }
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    if (delta != 0f) {
                        val previous = zoom
                        zoom = (zoom * if (delta < 0) 1.15f else 1 / 1.15f).coerceIn(1f, 8f)
                        // Réajuste le décalage pour que le point survolé reste
                        // approximativement sous le curseur.
                        if (zoom != previous) pan *= zoom / previous
                        if (zoom == 1f) pan = Offset.Zero
                    }
                }
                .pointerInput(flower.id, photoIndex) {
                    detectDragGestures { change, dragAmount ->
                        if (zoom > 1f) {
                            pan += dragAmount
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val bitmap by produceState<ImageBitmap?>(null, urls, photoIndex) {
                value = urls.getOrNull(photoIndex)?.let { ImageStore.load(it) }
            }

            if (bitmap == null) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = flower.species ?: "Photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (zoom > 1f) 0.dp else 40.dp)
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = pan.x,
                            translationY = pan.y,
                        )
                        // Double-clic : bascule entre « ajusté » et zoom 2×,
                        // comme la visionneuse de Windows.
                        .mouseInteractions(
                            onClick = {},
                            onDoubleClick = {
                                zoom = if (zoom > 1f) 1f else 2f
                                pan = Offset.Zero
                            },
                        ),
                )
            }

            // Flèches de navigation, visibles en permanence : sur un poste,
            // rien n'oblige à masquer les commandes pour gagner de la place.
            if (ordered.size > 1 || urls.size > 1) {
                IconButton(
                    onClick = { goTo(-1) },
                    modifier = Modifier.align(Alignment.CenterStart).padding(16.dp),
                ) {
                    Icon(
                        Icons.Filled.ChevronLeft,
                        contentDescription = "Précédente (flèche gauche)",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(
                    onClick = { goTo(1) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(16.dp),
                ) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Suivante (flèche droite)",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Fermer (Échap)",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = flower.speciesRef?.commonName
                        ?: flower.speciesRef?.scientificName
                        ?: flower.species
                        ?: "Espèce inconnue",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = com.florapin.desktop.screens.formatDate(flower.takenAt),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    if (urls.size > 1) {
                        Text(
                            text = "Photo ${photoIndex + 1}/${urls.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                    if (zoom > 1f) {
                        Text(
                            text = "Zoom ×%.1f".format(zoom),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
                if (flower.notes.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = flower.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth(0.6f),
                    )
                }
            }
        }
    }
}
