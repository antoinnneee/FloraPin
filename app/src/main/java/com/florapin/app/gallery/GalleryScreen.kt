package com.florapin.app.gallery

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.imageModel
import com.florapin.app.ui.components.EmptyState
import com.florapin.app.util.formatCaptureDate

/**
 * Galerie des fleurs capturées (NODE-9) : grille de vignettes, ou message si
 * vide. Le bouton flottant lance une nouvelle capture ; un appui sur une
 * vignette ouvre le détail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onCapture: () -> Unit,
    onFlowerClick: (Long) -> Unit,
    onOpenMap: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel(),
) {
    val flowers by viewModel.flowers.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mes fleurs") },
                actions = {
                    IconButton(onClick = onOpenMap) {
                        Text("🗺️", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onOpenFeed) {
                        Text("🌸", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onOpenFriends) {
                        Text("🤝", style = MaterialTheme.typography.titleLarge)
                    }
                    IconButton(onClick = onOpenProfile) {
                        Text("👤", style = MaterialTheme.typography.titleLarge)
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCapture) {
                Text("📷", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { innerPadding ->
        if (flowers.isEmpty()) {
            EmptyGallery(modifier = Modifier.padding(innerPadding))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(flowers, key = { it.id }) { flower ->
                    FlowerThumbnail(
                        flower = flower,
                        onClick = { onFlowerClick(flower.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier = Modifier) {
    EmptyState(
        title = "Aucune fleur pour l'instant",
        message = "Appuyez sur 📷 pour capturer votre première fleur.",
        modifier = modifier,
    )
}

@Composable
private fun FlowerThumbnail(
    flower: FlowerEntity,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
            AsyncImage(
                model = flower.imageModel(),
                contentDescription = "Fleur du ${formatCaptureDate(flower.createdAt)}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
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
