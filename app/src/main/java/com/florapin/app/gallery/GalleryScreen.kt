package com.florapin.app.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.albums.AddToAlbumSheet
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.thumbnailModel
import com.florapin.app.notifications.NotificationBell
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
    onOpenIdentify: () -> Unit,
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = viewModel(),
) {
    val flowers by viewModel.flowers.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val identifyBadge by viewModel.identifyBadge.collectAsStateWithLifecycle()
    val friendsBadge by viewModel.friendsBadge.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectionActive = selectedIds.isNotEmpty()

    var showAddToAlbum by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Recalcule les badges à l'affichage de la galerie (lancement + retour depuis
    // les écrans « à identifier » / amis, qui auront marqué leurs demandes vues).
    LaunchedEffect(Unit) { viewModel.refreshBadges() }

    // En mode sélection, le retour arrière annule la sélection plutôt que de quitter.
    BackHandler(enabled = selectionActive) { viewModel.clearSelection() }

    Scaffold(
        modifier = modifier,
        topBar = {
            if (selectionActive) {
                // Barre contextuelle : nombre de fleurs sélectionnées et actions
                // groupées (ajout album, suppression). Remplace la barre normale
                // tant que la sélection est active.
                SelectionTopBar(
                    count = selectedIds.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = viewModel::selectAll,
                    onAddToAlbum = { showAddToAlbum = true },
                    onDelete = { showDeleteConfirm = true },
                )
            } else {
                TopAppBar(
                    title = { Text("🌸 FloraPin") },
                    actions = {
                        // Topbar allégée : seules les entrées « à notifier » restent
                        // ici (identification demandée, invitations d'amis) plus la
                        // cloche du centre de notifications (TÂCHE 2.7). Le tri est
                        // descendu dans la vue, les albums dans la barre du bas.
                        BadgedEmojiAction("🔎", identifyBadge, onClick = onOpenIdentify)
                        BadgedEmojiAction("🤝", friendsBadge, onClick = onOpenFriends)
                        NotificationBell(onOpen = onOpenNotifications)
                    },
                )
            }
        },
        floatingActionButton = {
            // Le bouton de capture s'efface pendant la sélection : les actions du
            // moment sont celles de la barre contextuelle.
            if (!selectionActive) {
                FloatingActionButton(onClick = onCapture) {
                    Text("📷", style = MaterialTheme.typography.titleLarge)
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            SearchBar(
                query = query,
                onQueryChange = viewModel::setQuery,
            )
            // Tri rendu visible directement dans la vue, sous forme de pastille
            // affichant le critère courant en toutes lettres (façon badge). N'a de
            // sens que s'il y a des fleurs à trier.
            if (flowers.isNotEmpty()) {
                SortChip(selected = sort, onSelect = viewModel::setSort)
            }
            // Tirer vers le bas relance une passe de sync (si activée) et rafraîchit
            // les badges — la grille elle-même vient de Room (déjà à jour), device-first.
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
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
                                selected = flower.id in selectedIds,
                                // Tap : ouvre le détail hors sélection, bascule la
                                // case en mode sélection. Appui long : (dé)sélectionne
                                // — c'est aussi l'entrée dans le mode sélection.
                                onClick = {
                                    if (selectionActive) {
                                        viewModel.toggleSelection(flower.id)
                                    } else {
                                        onFlowerClick(flower.id)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(flower.id) },
                            )
                        }
                    }

                    query.isNotBlank() -> RefreshableEmpty {
                        EmptyState(
                            title = "Aucun résultat",
                            message = "Aucune fleur ne correspond à « $query ».",
                        )
                    }

                    else -> RefreshableEmpty { EmptyGallery() }
                }
            }
        }
    }

    // Ajout groupé à un album : réutilise la feuille du détail en mode lot. La
    // sélection est conservée après l'ajout (l'utilisateur peut enchaîner ou fermer).
    if (showAddToAlbum && selectionActive) {
        AddToAlbumSheet(
            flowerLocalIds = selectedIds.toList(),
            onDismiss = { showAddToAlbum = false },
        )
    }

    if (showDeleteConfirm && selectionActive) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Supprimer $count fleur${if (count > 1) "s" else ""} ?") },
            text = {
                Text(
                    "Cette action retire les fleurs sélectionnées de votre galerie " +
                        "sur tous vos appareils.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSelected()
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annuler") }
            },
        )
    }
}

/**
 * Barre du haut contextuelle du mode multi-sélection (TÂCHE 6.6) : croix de
 * sortie, nombre d'éléments sélectionnés et actions groupées (tout sélectionner,
 * ajouter à un album, supprimer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onAddToAlbum: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) { Text("✕") }
        },
        title = { Text("$count sélectionnée${if (count > 1) "s" else ""}") },
        actions = {
            IconButton(onClick = onSelectAll) { Text("☑️") }
            IconButton(onClick = onAddToAlbum) { Text("📁") }
            IconButton(onClick = onDelete) { Text("🗑️") }
        },
    )
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

/**
 * Pastille de tri façon « badge », posée dans la vue (et non dans la topbar).
 * Elle affiche le critère de tri courant en toutes lettres et ouvre le menu de
 * sélection au clic.
 */
@Composable
private fun SortChip(
    selected: GallerySort,
    onSelect: (GallerySort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
    ) {
        AssistChip(
            onClick = { expanded = true },
            leadingIcon = { Text("↕️") },
            label = { Text("Tri : ${selected.label}") },
        )
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
}

/**
 * Enveloppe un contenu « vide » (non défilable) dans une [LazyColumn] qui remplit
 * la zone, afin que le pull-to-refresh reste déclenchable même sans liste à faire
 * défiler (le geste doit trouver un conteneur défilable).
 */
@Composable
private fun RefreshableEmpty(content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillParentMaxSize()) { content() }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlowerThumbnail(
    flower: FlowerEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    // Nom de l'espèce si disponible (commun → scientifique → saisie libre),
    // sinon on retombe sur la date de capture.
    val name = flower.displayName()
    val cardModifier = Modifier
        .fillMaxWidth()
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .then(
            // Liseré primaire quand la vignette est sélectionnée.
            if (selected) {
                Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CardDefaults.shape,
                )
            } else {
                Modifier
            },
        )
    Card(modifier = cardModifier) {
        Column {
            Box {
                AsyncImage(
                    model = flower.thumbnailModel(),
                    contentDescription = name ?: "Fleur du ${formatCaptureDate(flower.createdAt)}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                if (selected) {
                    // Pastille de sélection posée en haut à droite de la photo.
                    Text(
                        text = "✅",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }
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
