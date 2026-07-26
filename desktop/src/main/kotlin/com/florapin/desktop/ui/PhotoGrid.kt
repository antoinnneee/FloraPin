package com.florapin.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.desktop.app.Selection

/**
 * Grille de vignettes, pièce maîtresse du compagnon.
 *
 * Le comportement vise l'Explorateur Windows plutôt qu'une galerie mobile :
 * survol qui révèle la case à cocher, clic qui sélectionne, double-clic qui
 * ouvre, clic droit qui propose des actions, et une taille de vignette réglable
 * — un grand écran doit pouvoir montrer beaucoup, ou montrer gros.
 */
@Composable
fun PhotoGrid(
    flowers: List<FlowerDto>,
    selection: Selection,
    thumbnailSize: Int,
    onOpen: (String) -> Unit,
    onContextMenu: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyContent: @Composable () -> Unit = {},
) {
    if (flowers.isEmpty()) {
        Box(modifier.fillMaxSize(), Alignment.Center) { emptyContent() }
        return
    }
    val orderedIds = remember(flowers) { flowers.map { it.id } }
    val gridState = rememberLazyGridState()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(thumbnailSize.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(flowers, key = { it.id }) { flower ->
            PhotoTile(
                flower = flower,
                selected = selection.contains(flower.id),
                onClick = { modifiers ->
                    selection.click(flower.id, orderedIds, modifiers.ctrl, modifiers.shift)
                },
                onDoubleClick = { onOpen(flower.id) },
                onSecondaryClick = {
                    // Convention Windows : un clic droit hors sélection
                    // recentre la sélection sur l'élément visé ; à l'intérieur,
                    // il la conserve pour permettre une action groupée.
                    if (!selection.contains(flower.id)) selection.selectOnly(flower.id)
                    onContextMenu(flower.id)
                },
            )
        }
    }
}

@Composable
private fun PhotoTile(
    flower: FlowerDto,
    selected: Boolean,
    onClick: (ClickModifiers) -> Unit,
    onDoubleClick: () -> Unit,
    onSecondaryClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val shape = RoundedCornerShape(12.dp)
    val label = flower.speciesRef?.commonName
        ?: flower.speciesRef?.scientificName
        ?: flower.species

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    hovered -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape,
            )
            .hoverable(interactionSource)
            .mouseInteractions(
                onClick = onClick,
                onDoubleClick = onDoubleClick,
                onSecondaryClick = onSecondaryClick,
            ),
    ) {
        AsyncPhoto(
            url = flower.previewPhotoUrls().firstOrNull(),
            modifier = Modifier.fillMaxSize(),
            contentDescription = label ?: "Photo de fleur",
        )

        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            )
        }

        // Légende lisible quelle que soit la photo : dégradé sombre en pied.
        if (label != null || flower.photos.size > 1) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (label != null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (flower.photos.size > 1) {
                        Text(
                            text = "  ${flower.photos.size} 📷",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }
        }

        if (flower.needsIdentification) {
            Icon(
                imageVector = Icons.Outlined.HelpOutline,
                contentDescription = "En attente d'identification",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(50))
                    .padding(3.dp)
                    .size(15.dp),
            )
        }

        // La case n'apparaît qu'au survol ou quand l'élément est déjà pris :
        // une grille au repos reste une grille de photos, pas un formulaire.
        if (hovered || selected) {
            Icon(
                imageVector = if (selected) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = if (selected) "Sélectionnée" else "Sélectionner",
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp),
            )
        }
    }
}
