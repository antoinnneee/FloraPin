package com.florapin.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.desktop.app.AppModel
import com.florapin.desktop.core.DesktopConfig
import com.florapin.desktop.map.DesktopMapStyle
import com.florapin.desktop.map.MapCamera
import com.florapin.desktop.map.MapMarker
import com.florapin.desktop.map.TileMapView
import com.florapin.desktop.ui.AsyncPhoto
import com.florapin.desktop.ui.EmptyState
import com.florapin.desktop.ui.UiActions

/**
 * Carte des photos géolocalisées, les miennes et celles qui me sont partagées.
 *
 * Les deux sources sont affichées ensemble par défaut — c'est l'intérêt de la
 * carte : voir où l'on est allé, et où sont allés ses amis — mais chacune peut
 * être masquée.
 */
@Composable
fun MapScreen(model: AppModel, actions: UiActions, camera: MapCamera) {
    var showMine by remember { mutableStateOf(true) }
    var showShared by remember { mutableStateOf(true) }
    var styleMenu by remember { mutableStateOf(false) }
    val style = DesktopMapStyle.fromId(model.preferences.mapStyleId)

    val markers = remember(model.myFlowers, model.sharedFlowers, showMine, showShared) {
        buildList {
            if (showMine) {
                model.myFlowers.forEach { flower ->
                    val lat = flower.latitude
                    val lon = flower.longitude
                    if (lat != null && lon != null) {
                        add(
                            MapMarker(
                                flower.id,
                                lat,
                                lon,
                                flower.previewPhotoUrls().firstOrNull(),
                                mine = true,
                            ),
                        )
                    }
                }
            }
            if (showShared) {
                model.sharedFlowers.forEach { flower ->
                    val lat = flower.latitude
                    val lon = flower.longitude
                    if (lat != null && lon != null) {
                        add(
                            MapMarker(
                                flower.id,
                                lat,
                                lon,
                                flower.previewPhotoUrls().firstOrNull(),
                                mine = false,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (DesktopConfig.maptilerApiKey.isBlank()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            EmptyState(
                title = "Carte non configurée",
                hint = "Ajoutez votre clé MapTiler dans les réglages pour afficher " +
                    "le fond de carte. Vos photos et leurs positions restent " +
                    "accessibles dans la photothèque.",
                icon = Icons.Outlined.OpenInFull,
            )
        }
        return
    }

    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            TileMapView(
                markers = markers,
                style = style,
                camera = camera,
                selectedId = model.selection.ids.singleOrNull(),
                onSelect = { model.selection.selectOnly(it) },
                onOpen = actions.openViewer,
                onContextMenu = { id ->
                    model.selection.selectOnly(id)
                    actions.openContextMenu()
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Commandes flottantes, à la manière des cartes en ligne.
            Row(
                Modifier.align(Alignment.TopStart).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    AssistChip(onClick = { styleMenu = true }, label = { Text(style.label) })
                    DropdownMenu(styleMenu, onDismissRequest = { styleMenu = false }) {
                        DesktopMapStyle.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    model.preferences.mapStyleId = option.id
                                    styleMenu = false
                                },
                            )
                        }
                    }
                }
                FilterChip(
                    selected = showMine,
                    onClick = { showMine = !showMine },
                    label = { Text("Mes photos") },
                )
                FilterChip(
                    selected = showShared,
                    onClick = { showShared = !showShared },
                    label = { Text("Partagées") },
                )
            }

            Column(
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = {
                    camera.moveTo(camera.latitude, camera.longitude, camera.zoom + 1)
                }) { Icon(Icons.Filled.Add, contentDescription = "Zoomer") }
                FilledTonalIconButton(onClick = {
                    camera.moveTo(camera.latitude, camera.longitude, camera.zoom - 1)
                }) { Icon(Icons.Filled.Remove, contentDescription = "Dézoomer") }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            ) {
                Text(
                    text = "${markers.size} photo(s) situées  ·  glisser pour déplacer, " +
                        "molette pour zoomer, double-clic pour ouvrir",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }

        // Aperçu de la photo pointée : évite d'ouvrir la visionneuse pour
        // simplement savoir « qu'est-ce que j'ai photographié là ? ».
        model.selection.ids.singleOrNull()?.let(model::flowerById)?.let { flower ->
            Surface(Modifier.width(300.dp).fillMaxHeight(), tonalElevation = 1.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncPhoto(
                        url = flower.previewPhotoUrls().firstOrNull(),
                        modifier = Modifier.fillMaxWidth().height(210.dp),
                    )
                    Text(
                        text = flower.speciesRef?.commonName
                            ?: flower.speciesRef?.scientificName
                            ?: flower.species
                            ?: "Espèce inconnue",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = formatDate(flower.takenAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!model.isMine(flower) && flower.ownerName.isNotBlank()) {
                        Text(
                            text = "Partagée par ${flower.ownerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (flower.notes.isNotBlank()) {
                        Card {
                            Text(
                                text = flower.notes,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { actions.openViewer(flower.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.OpenInFull, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ouvrir en grand")
                    }
                }
            }
        }
    }
}
