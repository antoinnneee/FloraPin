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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.desktop.app.AppModel
import com.florapin.desktop.app.Section
import com.florapin.desktop.app.SortOrder
import com.florapin.desktop.ui.AsyncPhoto
import com.florapin.desktop.ui.EmptyState
import com.florapin.desktop.ui.PhotoGrid
import com.florapin.desktop.ui.UiActions
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Focus du champ de recherche, déclenché par Ctrl+F depuis la fenêtre. */
val SearchFocusRequester = FocusRequester()

/**
 * Photothèque : la vue par défaut du compagnon, et celle des sections « Albums »
 * et « Partagées avec moi », qui n'en diffèrent que par la source des photos.
 */
@Composable
fun LibraryScreen(model: AppModel, actions: UiActions) {
    val flowers = model.visibleFlowers
    Column(Modifier.fillMaxSize()) {
        LibraryToolbar(model, actions)
        HorizontalDivider()
        Row(Modifier.weight(1f)) {
            PhotoGrid(
                flowers = flowers,
                selection = model.selection,
                thumbnailSize = model.preferences.thumbnailSize,
                onOpen = actions.openViewer,
                onContextMenu = { actions.openContextMenu() },
                modifier = Modifier.weight(1f),
                emptyContent = {
                    if (model.query.isNotBlank() || model.onlyUnidentified) {
                        EmptyState(
                            title = "Aucun résultat",
                            hint = "Aucune photo ne correspond à ce filtre. " +
                                "Effacez la recherche pour tout revoir.",
                            icon = Icons.Filled.Search,
                        )
                    } else if (model.section == Section.SHARED) {
                        EmptyState(
                            title = "Rien de partagé pour l'instant",
                            hint = "Les photos que vos amis partagent avec vous " +
                                "apparaîtront ici.",
                        )
                    } else {
                        EmptyState(
                            title = "Aucune photo",
                            hint = "Prenez vos photos depuis l'application mobile : " +
                                "elles arriveront ici automatiquement.",
                        )
                    }
                },
            )
            if (model.preferences.showDetailsPane) {
                HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))
                DetailsPane(model, actions, Modifier.width(320.dp))
            }
        }
    }
}

@Composable
private fun LibraryToolbar(model: AppModel, actions: UiActions) {
    var sortMenu by remember { mutableStateOf(false) }
    val selectedCount = model.selection.size

    Surface(tonalElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = model.query,
                    onValueChange = { model.query = it },
                    placeholder = { Text("Rechercher une espèce, une note, un tag…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (model.query.isNotEmpty()) {
                            IconButton(onClick = { model.query = "" }) { Text("✕") }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(SearchFocusRequester),
                )

                FilterChip(
                    selected = model.onlyUnidentified,
                    onClick = { model.onlyUnidentified = !model.onlyUnidentified },
                    label = { Text("À identifier") },
                    leadingIcon = {
                        Icon(Icons.Outlined.HelpOutline, null, Modifier.size(16.dp))
                    },
                )

                Box {
                    AssistChip(
                        onClick = { sortMenu = true },
                        label = { Text(model.sortOrder.label) },
                        leadingIcon = { Icon(Icons.Filled.Sort, null, Modifier.size(16.dp)) },
                    )
                    DropdownMenu(sortMenu, onDismissRequest = { sortMenu = false }) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    model.sortOrder = order
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }

                IconButton(onClick = { model.refreshAll() }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Actualiser (F5)")
                }
                IconButton(
                    onClick = {
                        model.preferences.showDetailsPane = !model.preferences.showDetailsPane
                    },
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = "Panneau d'informations")
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = actions.exportSelection,
                    enabled = selectedCount > 0,
                ) {
                    Icon(Icons.Outlined.SaveAlt, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (selectedCount > 0) {
                            "Récupérer $selectedCount photo(s)"
                        } else {
                            "Récupérer mes photos"
                        },
                    )
                }
                OutlinedButton(onClick = actions.addSelectionToAlbum, enabled = selectedCount > 0) {
                    Icon(Icons.Outlined.LibraryAdd, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ajouter à un album")
                }
                if (model.section != Section.SHARED) {
                    OutlinedButton(
                        onClick = actions.requestIdentification,
                        enabled = selectedCount > 0,
                    ) {
                        Icon(Icons.Outlined.HelpOutline, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Demander l'identification")
                    }
                }

                Spacer(Modifier.weight(1f))

                Text("Taille", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = model.preferences.thumbnailSize.toFloat(),
                    onValueChange = { model.preferences.thumbnailSize = it.toInt() },
                    valueRange = 110f..380f,
                    modifier = Modifier.width(140.dp),
                )
            }
        }
    }
}

/**
 * Panneau latéral : détail de la photo sélectionnée, ou récapitulatif d'une
 * sélection multiple. Sur mobile, ces informations occupent un écran entier ;
 * ici elles restent visibles pendant que l'on continue de parcourir la grille.
 */
@Composable
private fun DetailsPane(model: AppModel, actions: UiActions, modifier: Modifier = Modifier) {
    val selected = model.selection.ids
    val flower = selected.singleOrNull()?.let(model::flowerById)

    Surface(modifier.fillMaxHeight(), tonalElevation = 1.dp) {
        when {
            flower != null -> FlowerDetails(model, actions, flower)
            selected.size > 1 -> Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${selected.size} photos", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Utilisez la barre d'outils ou le clic droit pour agir sur " +
                        "l'ensemble de la sélection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(
                    text = "Sélectionnez une photo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun FlowerDetails(model: AppModel, actions: UiActions, flower: FlowerDto) {
    val mine = model.isMine(flower)
    var editingNotes by remember(flower.id) { mutableStateOf(false) }
    var notes by remember(flower.id) { mutableStateOf(flower.notes) }
    var species by remember(flower.id) { mutableStateOf(flower.species.orEmpty()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncPhoto(
            url = flower.previewPhotoUrls().firstOrNull(),
            modifier = Modifier.fillMaxWidth().height(200.dp),
        )

        Text(
            text = flower.speciesRef?.commonName
                ?: flower.speciesRef?.scientificName
                ?: flower.species
                ?: "Espèce inconnue",
            style = MaterialTheme.typography.titleLarge,
        )
        flower.speciesRef?.scientificName?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        DetailRow("Prise le", formatDate(flower.takenAt))
        if (!mine && flower.ownerName.isNotBlank()) DetailRow("Partagée par", flower.ownerName)
        if (flower.latitude != null && flower.longitude != null) {
            DetailRow(
                "Position",
                "%.5f, %.5f".format(Locale.FRANCE, flower.latitude, flower.longitude),
            )
            TextButton(onClick = { actions.showOnMap(flower.id) }) {
                Icon(Icons.Outlined.Place, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Voir sur la carte")
            }
        } else {
            DetailRow("Position", "Non renseignée")
        }
        if (flower.tags.isNotEmpty()) DetailRow("Tags", flower.tags.joinToString(", "))
        if (flower.photos.size > 1) DetailRow("Photos", "${flower.photos.size} dans cette fiche")

        HorizontalDivider()

        if (mine) {
            Text("Espèce", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = species,
                onValueChange = { species = it },
                placeholder = { Text("Nom de l'espèce") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Notes", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = notes,
                onValueChange = {
                    notes = it
                    editingNotes = true
                },
                placeholder = { Text("Vos observations…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        model.updateFlower(
                            flower.id,
                            notes = notes,
                            species = species.ifBlank { null },
                        )
                        editingNotes = false
                    },
                    enabled = editingNotes || species != flower.species.orEmpty(),
                ) { Text("Enregistrer") }
                OutlinedButton(
                    onClick = {
                        notes = flower.notes
                        species = flower.species.orEmpty()
                        editingNotes = false
                    },
                ) { Text("Annuler") }
            }

            HorizontalDivider()
            if (flower.needsIdentification) {
                Text(
                    text = "Vos amis ont été sollicités pour identifier cette fleur.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { model.cancelIdentification(flower.id) }) {
                    Text("Annuler la demande")
                }
            } else {
                OutlinedButton(onClick = { model.requestIdentification(listOf(flower.id)) }) {
                    Icon(Icons.Outlined.HelpOutline, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Demander l'identification")
                }
            }
            TextButton(
                onClick = actions.deleteSelection,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Supprimer")
            }
        } else if (flower.notes.isNotBlank()) {
            Text("Notes", style = MaterialTheme.typography.labelLarge)
            Text(flower.notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            overflow = TextOverflow.Ellipsis,
            maxLines = 3,
        )
    }
}

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy 'à' HH'h'mm", Locale.FRANCE)

/** Date lisible ; l'horodatage brut est renvoyé tel quel s'il est illisible. */
fun formatDate(iso: String): String =
    runCatching { OffsetDateTime.parse(iso).format(DATE_FORMAT) }.getOrDefault(iso)
