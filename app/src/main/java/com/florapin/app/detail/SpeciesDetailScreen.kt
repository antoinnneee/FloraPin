package com.florapin.app.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.aspectRatio
import coil.compose.AsyncImage
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.imageModel
import com.florapin.app.network.dto.SpeciesDto

/**
 * Fiche d'une espèce (NODE-151) : nom scientifique + commun + famille +
 * description, et la grille « mes fleurs de cette espèce » (filtre local).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesDetailScreen(
    speciesId: String,
    onBack: () -> Unit,
    onFlowerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpeciesDetailViewModel = viewModel(
        factory = SpeciesDetailViewModel.factory(LocalContext.current, speciesId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val species = state.species

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = species?.let { "${it.emoji ?: "🌸"} ${it.scientificName}" }
                            ?: "Espèce",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { innerPadding ->
        when {
            species == null && state.error != null -> Centered(innerPadding) {
                Text(state.error ?: "Erreur")
            }
            species == null -> Centered(innerPadding) { Text("Chargement…") }
            else -> SpeciesContent(
                species = species,
                flowers = state.flowers,
                onFlowerClick = onFlowerClick,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun SpeciesContent(
    species: SpeciesDto,
    flowers: List<FlowerEntity>,
    onFlowerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 108.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(species.commonName, style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = "Famille : ${species.family}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (species.description.isNotBlank()) {
                    Text(species.description, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    text = "🌼 Mes fleurs de cette espèce (${flowers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (flowers.isEmpty()) {
                    Text(
                        text = "Aucune fleur pour le moment.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(flowers, key = { it.id }) { flower ->
            AsyncImage(
                model = flower.imageModel(),
                contentDescription = "Fleur",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onFlowerClick(flower.id) },
            )
        }
    }
}

@Composable
private fun Centered(
    innerPadding: androidx.compose.foundation.layout.PaddingValues,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) { content() }
}
