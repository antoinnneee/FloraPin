package com.florapin.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.desktop.app.AppModel
import com.florapin.desktop.ui.AsyncPhoto
import com.florapin.desktop.ui.EmptyState
import com.florapin.desktop.ui.PhotoGrid
import com.florapin.desktop.ui.UiActions
import com.florapin.desktop.ui.mouseInteractions

/**
 * Gestion des albums : liste à gauche, contenu à droite.
 *
 * Cette disposition — impossible à tenir sur un téléphone — est ce qui rend le
 * classement agréable au bureau : on garde la liste sous les yeux tout en
 * parcourant les photos d'un album.
 */
@Composable
fun AlbumsScreen(model: AppModel, actions: UiActions) {
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AlbumDto?>(null) }
    var deleting by remember { mutableStateOf<AlbumDto?>(null) }
    val album = model.selectedAlbum()

    Row(Modifier.fillMaxSize()) {
        Surface(Modifier.width(280.dp).fillMaxHeight(), tonalElevation = 1.dp) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Nouvel album")
                    }
                }
                if (model.albums.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            text = "Aucun album.\nCréez-en un pour ranger vos photos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(model.albums, key = { it.id }) { entry ->
                            AlbumRow(
                                model = model,
                                album = entry,
                                selected = entry.id == model.selectedAlbumId,
                                onClick = {
                                    model.selectedAlbumId = entry.id
                                    model.selection.clear()
                                },
                                onRename = { renaming = entry },
                                onDelete = { deleting = entry },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))

        Column(Modifier.weight(1f)) {
            if (album == null) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
                        title = "Choisissez un album",
                        hint = "Sélectionnez un album à gauche pour en voir le contenu, " +
                            "ou créez-en un nouveau.",
                        icon = Icons.Outlined.LibraryAdd,
                    )
                }
            } else {
                AlbumHeader(model, actions, album)
                HorizontalDivider()
                PhotoGrid(
                    flowers = model.visibleFlowers,
                    selection = model.selection,
                    thumbnailSize = model.preferences.thumbnailSize,
                    onOpen = actions.openViewer,
                    onContextMenu = { actions.openContextMenu() },
                    modifier = Modifier.weight(1f),
                    emptyContent = {
                        EmptyState(
                            title = "Album vide",
                            hint = "Sélectionnez des photos dans la photothèque, " +
                                "puis « Ajouter à un album ».",
                            icon = Icons.Outlined.Image,
                        )
                    },
                )
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Nouvel album",
            initial = "",
            confirmLabel = "Créer",
            onConfirm = {
                model.createAlbum(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }

    renaming?.let { target ->
        NameDialog(
            title = "Renommer l'album",
            initial = target.name,
            confirmLabel = "Renommer",
            onConfirm = {
                model.renameAlbum(target.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer « ${target.name} » ?") },
            text = {
                Text(
                    "L'album est supprimé, mais les photos qu'il contient restent " +
                        "dans votre photothèque.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    model.deleteAlbum(target.id)
                    deleting = null
                }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun AlbumHeader(model: AppModel, actions: UiActions, album: AlbumDto) {
    val selectedCount = model.selection.size
    Surface(tonalElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(album.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = buildString {
                        append("${model.visibleFlowers.size} photo(s)")
                        if (album.groupId != null) append("  ·  album partagé")
                        if (!album.canEdit) append("  ·  lecture seule")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = actions.exportSelection, enabled = selectedCount > 0) {
                Icon(Icons.Outlined.SaveAlt, null, Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Récupérer la sélection")
            }
            if (album.canEdit) {
                OutlinedButton(
                    onClick = {
                        model.selection.ids.singleOrNull()?.let {
                            model.setAlbumCover(album.id, it)
                        }
                    },
                    enabled = selectedCount == 1,
                ) {
                    Icon(Icons.Outlined.Image, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Couverture")
                }
                OutlinedButton(
                    onClick = { model.removeFromAlbum(album.id, model.selection.ids.toList()) },
                    enabled = selectedCount > 0,
                ) {
                    Icon(Icons.Outlined.PlaylistRemove, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retirer de l'album")
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(
    model: AppModel,
    album: AlbumDto,
    selected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    // Couverture explicite si elle est définie, sinon première photo connue :
    // une pastille grise pour un album rempli serait déroutante.
    val coverUrl = remember(album, model.myFlowers) {
        val cover = album.coverFlowerId?.let(model::flowerById)
            ?: album.flowers.firstOrNull()
            ?: album.flowerIds.firstNotNullOfOrNull(model::flowerById)
        cover?.previewPhotoUrls()?.firstOrNull()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else if (hovered) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .hoverable(interactionSource)
            .mouseInteractions(onClick = { onClick() })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncPhoto(
            url = coverUrl,
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${album.flowerIds.size} photo(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Les commandes n'apparaissent qu'au survol pour garder une liste calme.
        if (hovered && album.canEdit) {
            IconButton(onClick = onRename, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Outlined.DriveFileRenameOutline,
                    contentDescription = "Renommer",
                    modifier = Modifier.size(17.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Supprimer",
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom de l'album") },
                singleLine = true,
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
