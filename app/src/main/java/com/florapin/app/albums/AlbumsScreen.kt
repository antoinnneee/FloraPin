package com.florapin.app.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.data.AlbumEntity
import com.florapin.app.ui.components.EmptyState

/**
 * Liste des albums (NODE-103) : création via FAB, renommage/suppression par
 * ligne, ouverture d'un album au clic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = viewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AlbumEntity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Albums") },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) { Text("➕") }
        },
    ) { innerPadding ->
        if (albums.isEmpty()) {
            EmptyState(
                title = "Aucun album",
                message = "Appuyez sur ➕ pour créer votre premier album.",
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumRow(
                        album = album,
                        onClick = { onOpenAlbum(album.id) },
                        onRename = { renaming = album },
                        onDelete = { viewModel.delete(album) },
                    )
                }
            }
        }
    }

    if (showCreate) {
        AlbumNameDialog(
            title = "Nouvel album",
            initialName = "",
            onConfirm = { viewModel.create(it); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
    renaming?.let { album ->
        AlbumNameDialog(
            title = "Renommer l'album",
            initialName = album.name,
            onConfirm = { viewModel.rename(album, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun AlbumRow(
    album: AlbumEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRename) { Text("✏️") }
            IconButton(onClick = onDelete) { Text("🗑️") }
        }
    }
}

/** Dialogue de saisie d'un nom d'album (création ou renommage). */
@Composable
private fun AlbumNameDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
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
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
