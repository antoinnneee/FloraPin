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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onOpenFriends: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenIdentify: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel(),
) {
    val flowers by viewModel.flowers.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val identifyBadge by viewModel.identifyBadge.collectAsStateWithLifecycle()
    val friendsBadge by viewModel.friendsBadge.collectAsStateWithLifecycle()

    // Recalcule les badges à l'affichage de la galerie (lancement + retour depuis
    // les écrans « à identifier » / amis, qui auront marqué leurs demandes vues).
    LaunchedEffect(Unit) { viewModel.refreshBadges() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("🌸 FloraPin") },
                actions = {
                    SortMenu(selected = sort, onSelect = viewModel::setSort)
                    IconButton(onClick = onOpenAlbums) {
                        Text("📁", style = MaterialTheme.typography.titleLarge)
                    }
                    BadgedEmojiAction("🔎", identifyBadge, onClick = onOpenIdentify)
                    BadgedEmojiAction("🤝", friendsBadge, onClick = onOpenFriends)
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCapture) {
                Text("📷", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SearchBar(
                query = query,
                onQueryChange = viewModel::setQuery,
            )
            when {
                flowers.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
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

                query.isNotBlank() -> EmptyState(
                    title = "Aucun résultat",
                    message = "Aucune fleur ne correspond à « $query ».",
                )

                else -> EmptyGallery()
            }
        }
    }
}

/**
 * Action emoji de la barre du haut, surmontée d'un badge de nouveautés quand
 * [badge] > 0 (au-delà de 99, affiche « 99+ »).
 */
@Composable
private fun BadgedEmojiAction(
    emoji: String,
    badge: Int,
    onClick: () -> Unit,
) {
    BadgedBox(
        badge = {
            if (badge > 0) {
                Badge { Text(if (badge > 99) "99+" else "$badge") }
            }
        },
    ) {
        IconButton(onClick = onClick) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** Barre de recherche par espèce, notes ou étiquette (NODE-120). */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        leadingIcon = { Text("🔍") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Text("✕") }
            }
        },
        placeholder = { Text("Rechercher (espèce, notes, étiquette)") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Menu déroulant de choix du tri. */
@Composable
private fun SortMenu(
    selected: GallerySort,
    onSelect: (GallerySort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Text("↕️", style = MaterialTheme.typography.titleLarge)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        GallerySort.entries.forEach { order ->
            DropdownMenuItem(
                text = {
                    val mark = if (order == selected) "✓ " else ""
                    Text("$mark${order.label}")
                },
                onClick = {
                    onSelect(order)
                    expanded = false
                },
            )
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
    // Nom de l'espèce si disponible (commun → scientifique → saisie libre),
    // sinon on retombe sur la date de capture.
    val name = flower.displayName()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column {
            AsyncImage(
                model = flower.thumbnailModel(),
                contentDescription = name ?: "Fleur du ${formatCaptureDate(flower.createdAt)}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Text(
                text = name ?: formatCaptureDate(flower.createdAt),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

/** Nom d'espèce affichable (commun, scientifique ou saisie libre), ou null. */
private fun FlowerEntity.displayName(): String? =
    speciesCommonName?.takeIf { it.isNotBlank() }
        ?: speciesScientificName?.takeIf { it.isNotBlank() }
        ?: species?.takeIf { it.isNotBlank() }
