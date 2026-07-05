package com.florapin.app.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.thumbnailModel
import com.florapin.app.ui.components.EmojiIcon
import com.florapin.app.ui.components.EmptyState
import com.florapin.app.util.formatCaptureDate

/**
 * Écran d'un album (NODE-103) : grille de ses fleurs, renommage, retrait d'une
 * fleur par appui long. Un appui ouvre le détail de la fleur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    onFlowerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = viewModel(),
    collaborationViewModel: AlbumCollaborationViewModel = viewModel(),
) {
    viewModel.setAlbumId(albumId)
    val album by viewModel.album.collectAsStateWithLifecycle()
    val flowers by viewModel.flowers.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf(false) }
    var showCollaboration by remember { mutableStateOf(false) }

    // Recharge l'état de collaboration quand l'album (ou son groupe) change.
    androidx.compose.runtime.LaunchedEffect(album?.id, album?.groupId) {
        album?.let { collaborationViewModel.load(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(album?.name ?: "Album") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        EmojiIcon("←", contentDescription = "Retour")
                    }
                },
                actions = {
                    val current = album
                    if (current != null) {
                        // Collaboration (TÂCHE 7.1) : disponible sur un album
                        // synchronisé (serverId requis pour l'API groupes).
                        if (current.serverId != null) {
                            IconButton(onClick = { showCollaboration = true }) {
                                EmojiIcon("👥", contentDescription = "Collaboration")
                            }
                        }
                        // Renommage réservé à qui peut éditer (owner ou droits).
                        if (current.canEdit) {
                            IconButton(onClick = { renaming = true }) {
                                EmojiIcon("✏️", contentDescription = "Renommer l'album")
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (flowers.isEmpty()) {
            EmptyState(
                title = "Album vide",
                message = "Ajoutez des fleurs depuis leur écran de détail.",
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val canEdit = album?.canEdit ?: true
                items(flowers, key = { it.id }) { flower ->
                    AlbumFlowerThumbnail(
                        flower = flower,
                        onClick = { onFlowerClick(flower.id) },
                        // Retrait par appui long réservé à qui peut éditer l'album.
                        onLongClick = { if (canEdit) viewModel.removeFlower(flower.id) },
                    )
                }
            }
        }
    }

    if (showCollaboration) {
        val current = album
        if (current != null) {
            val collabState by collaborationViewModel.state.collectAsStateWithLifecycle()
            AlbumCollaborationPanel(
                album = current,
                // Album solo → je suis le propriétaire de ma copie ; album de
                // groupe → l'info vient du groupe chargé.
                isOwner = if (collabState.group == null) true else collabState.isOwner,
                viewModel = collaborationViewModel,
                onDismiss = { showCollaboration = false },
            )
        }
    }

    if (renaming) {
        val current = album
        var name by remember { mutableStateOf(current?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renommer l'album") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de l'album") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.rename(name); renaming = false },
                    enabled = name.isNotBlank(),
                ) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = false }) { Text("Annuler") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumFlowerThumbnail(
    flower: FlowerEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            AsyncImage(
                model = flower.thumbnailModel(),
                contentDescription = "Fleur du ${formatCaptureDate(flower.createdAt)}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )
            Text(
                text = formatCaptureDate(flower.createdAt),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
