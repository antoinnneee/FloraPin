package com.florapin.app.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Feuille « Ajouter à un album » (NODE-103) : liste les albums et rattache la
 * fleur [flowerLocalId] à celui choisi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToAlbumSheet(
    flowerLocalId: Long,
    onDismiss: () -> Unit,
    viewModel: AlbumsViewModel = viewModel(),
) = AddToAlbumSheet(
    flowerLocalIds = listOf(flowerLocalId),
    onDismiss = onDismiss,
    viewModel = viewModel,
)

/**
 * Variante en lot (multi-sélection de la galerie — TÂCHE 6.6) : rattache toutes
 * les fleurs [flowerLocalIds] à l'album choisi. Réutilise la même feuille que le
 * cas unitaire ; le titre reflète le nombre de fleurs concernées.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToAlbumSheet(
    flowerLocalIds: List<Long>,
    onDismiss: () -> Unit,
    viewModel: AlbumsViewModel = viewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val count = flowerLocalIds.size
    val title = if (count > 1) {
        "Ajouter $count fleurs à un album"
    } else {
        "Ajouter à un album"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)

            if (albums.isEmpty()) {
                Text(
                    "Aucun album. Créez-en un depuis l'onglet Albums.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(albums, key = { it.id }) { album ->
                        Text(
                            text = album.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addFlowersToAlbum(album.id, flowerLocalIds)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
