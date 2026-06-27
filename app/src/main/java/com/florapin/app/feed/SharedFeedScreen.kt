package com.florapin.app.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.likes.LikeButton
import com.florapin.app.ui.components.EmptyState

/**
 * Feed des fleurs partagées avec moi (NODE-72) : photo (URL présignée via Coil),
 * espèce, ami partageur, et coordonnées si le partage inclut le GPS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedFeedScreen(
    modifier: Modifier = Modifier,
    viewModel: SharedFeedViewModel = viewModel(
        factory = SharedFeedViewModel.factory(androidx.compose.ui.platform.LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Partagées avec moi") },
            )
        },
    ) { innerPadding ->
        if (!state.loading && state.items.isEmpty()) {
            EmptyState(
                title = state.error?.let { "Oups" } ?: "Rien de partagé",
                message = state.error
                    ?: "Aucune fleur partagée avec vous pour l'instant.",
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SortBar(selected = state.sort, onSelect = viewModel::setSort)
            }
            items(state.items, key = { it.flower.id }) { item ->
                SharedFlowerCard(
                    item = item,
                    onToggleLike = { viewModel.toggleLike(item.flower.id) },
                )
            }
        }
    }
}

/** Sélecteur d'ordre du feed (récentes / meilleures photos, NODE-140). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBar(selected: FeedSort, onSelect: (FeedSort) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FeedSort.entries.forEach { sort ->
            FilterChip(
                selected = sort == selected,
                onClick = { onSelect(sort) },
                label = { Text(sort.label) },
            )
        }
    }
}

@Composable
private fun SharedFlowerCard(
    item: SharedFlowerItem,
    onToggleLike: () -> Unit,
) {
    val flower = item.flower
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            AsyncImage(
                // Preview léger : miniature WebP si disponible, sinon plein.
                model = flower.thumbnailUrl ?: flower.imageUrl,
                contentDescription = "Fleur partagée",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.ownerName?.let {
                        Text("Partagée par $it", style = MaterialTheme.typography.bodyMedium)
                    } ?: Box(Modifier)
                    LikeButton(
                        liked = flower.likedByMe,
                        count = flower.likeCount,
                        onToggle = onToggleLike,
                    )
                }
                flower.species?.takeIf { it.isNotBlank() }?.let {
                    Text("🌿 $it", style = MaterialTheme.typography.bodyMedium)
                }
                val lat = flower.latitude
                val lng = flower.longitude
                if (lat != null && lng != null) {
                    Text(
                        text = "📍 %.5f, %.5f".format(lat, lng),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = "📍 Position non partagée",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
