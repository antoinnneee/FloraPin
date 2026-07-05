package com.florapin.app.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
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
import com.florapin.app.util.formatMonthLabel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    val density by viewModel.density.collectAsStateWithLifecycle()
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
            // Tri + densité rendus visibles directement dans la vue, sous forme de
            // pastilles (façon badge). N'ont de sens que s'il y a des fleurs à
            // afficher.
            if (flowers.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SortChip(selected = sort, onSelect = viewModel::setSort)
                    DensityChip(selected = density, onSelect = viewModel::setDensity)
                }
            }
            // Regroupement par mois (TÂCHE 6.7) : n'a de sens que quand la liste est
            // triée par date. Pour le tri par espèce, on reste en grille à plat.
            val grouped = sort == GallerySort.DATE_DESC || sort == GallerySort.DATE_ASC
            val rows = remember(flowers, grouped) {
                if (grouped) {
                    flowers.groupedByMonth()
                } else {
                    // Tri par espèce : pas de regroupement, la clé de mois n'est pas
                    // utilisée (ni en-têtes ni fast scroller).
                    flowers.map { GalleryRow.Flower(it, "") }
                }
            }
            val gridState = rememberLazyGridState()
            // Tirer vers le bas relance une passe de sync (si activée) et rafraîchit
            // les badges — la grille elle-même vient de Room (déjà à jour), device-first.
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    flowers.isNotEmpty() -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = density.minCellSize),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            rows.forEach { row ->
                                when (row) {
                                    is GalleryRow.MonthHeader -> item(
                                        key = "month-${row.monthKey}",
                                        // L'en-tête occupe toute la largeur de la grille.
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) {
                                        MonthHeader(row.label)
                                    }

                                    is GalleryRow.Flower -> item(key = row.flower.id) {
                                        val flower = row.flower
                                        FlowerThumbnail(
                                            flower = flower,
                                            selected = flower.id in selectedIds,
                                            // Tap : ouvre le détail hors sélection, bascule
                                            // la case en mode sélection. Appui long :
                                            // (dé)sélectionne — c'est aussi l'entrée dans le
                                            // mode sélection.
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
                            }
                        }
                        // Fast scroller : poignée latérale de défilement rapide avec
                        // bulle du mois pointé. N'apparaît (et n'est utile) que quand la
                        // grille groupée déborde de l'écran.
                        val canScroll by remember {
                            derivedStateOf { gridState.canScrollForward || gridState.canScrollBackward }
                        }
                        if (grouped && canScroll) {
                            MonthFastScroller(
                                gridState = gridState,
                                rows = rows,
                                modifier = Modifier.align(Alignment.CenterEnd),
                            )
                        }
                    }

                    query.isNotBlank() -> RefreshableEmpty {
                        EmptyState(
                            title = "Aucun résultat",
                            message = "Aucune fleur ne correspond à « $query ».",
                        )
                    }

                    else -> RefreshableEmpty { EmptyGallery(onCapture = onCapture) }
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
 * Pastille de densité de la grille (TÂCHE 6.8), posée dans la vue à côté du tri.
 * Elle affiche le palier courant et ouvre le menu de sélection au clic ; le choix
 * est persisté (store dédié) via le ViewModel.
 */
@Composable
private fun DensityChip(
    selected: GalleryDensity,
    onSelect: (GalleryDensity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.padding(end = 12.dp, bottom = 4.dp),
    ) {
        AssistChip(
            onClick = { expanded = true },
            leadingIcon = { Text("▦") },
            label = { Text("Densité : ${selected.label}") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GalleryDensity.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        val mark = if (option == selected) "✓ " else ""
                        Text("$mark${option.label}")
                    },
                    onClick = {
                        onSelect(option)
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
private fun EmptyGallery(
    modifier: Modifier = Modifier,
    onCapture: (() -> Unit)? = null,
) {
    EmptyState(
        title = "Aucune fleur pour l'instant",
        message = "Capturez votre première fleur pour commencer votre herbier.",
        modifier = modifier,
        actionLabel = onCapture?.let { "📷 Capturer une fleur" },
        onAction = onCapture,
    )
}

/**
 * En-tête de mois (TÂCHE 6.7) : titre du mois de capture, posé en pleine largeur
 * au-dessus des vignettes du mois correspondant.
 */
@Composable
private fun MonthHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    )
}

/** Hauteur de la poignée du fast scroller. */
private val FastScrollerThumbHeight = 48.dp

/**
 * Poignée latérale de défilement rapide (TÂCHE 6.7). On la fait glisser
 * verticalement pour parcourir la galerie ; pendant le glissement, une bulle
 * affiche le mois pointé. La position de la poignée reflète, hors glissement,
 * l'élément visible en tête de grille.
 */
@Composable
private fun BoxScope.MonthFastScroller(
    gridState: LazyGridState,
    rows: List<GalleryRow>,
    modifier: Modifier = Modifier,
) {
    val itemCount = rows.size
    if (itemCount == 0) return
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }

    // Fraction de défilement déduite de l'élément visible en tête (hors glissement).
    val scrollFraction by remember {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            if (total <= 1) 0f else gridState.firstVisibleItemIndex.toFloat() / (total - 1)
        }
    }
    val fraction = (if (dragging) dragFraction else scrollFraction).coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp),
    ) {
        val density = LocalDensity.current
        val trackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(density) { FastScrollerThumbHeight.toPx() }
        val maxOffset = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = (fraction * maxOffset).coerceIn(0f, maxOffset)

        // Mois pointé, pour la bulle : l'en-tête le plus proche de l'index visé.
        val targetIndex = (fraction * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
        val bubbleLabel = rows.getOrNull(targetIndex)?.monthLabel()

        // Applique une position (Y relatif au rail) : met à jour la fraction et
        // défile la grille sur l'élément correspondant.
        fun seek(y: Float) {
            val f = if (trackHeightPx <= 0f) 0f else (y / trackHeightPx).coerceIn(0f, 1f)
            dragFraction = f
            val idx = (f * (itemCount - 1)).roundToInt().coerceIn(0, itemCount - 1)
            scope.launch { gridState.scrollToItem(idx) }
        }

        // Rail plein-hauteur capturant le glissement vertical.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp)
                .pointerInput(itemCount, trackHeightPx) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            seek(offset.y)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            seek(change.position.y)
                        },
                    )
                },
        ) {
            // Poignée.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                    .padding(end = 4.dp)
                    .width(6.dp)
                    .height(FastScrollerThumbHeight)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        // Bulle du mois pointé, à gauche de la poignée, pendant le glissement.
        if (dragging && !bubbleLabel.isNullOrBlank()) {
            val bubbleGapPx = with(density) { 44.dp.toPx() }.roundToInt()
            Text(
                text = bubbleLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = -bubbleGapPx,
                            y = (thumbOffsetPx + (thumbHeightPx - with(density) { 32.dp.toPx() }) / 2f)
                                .roundToInt(),
                        )
                    }
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

/** Libellé de mois lisible d'une ligne (en-tête : son libellé ; fleur : dérivé). */
private fun GalleryRow.monthLabel(): String = when (this) {
    is GalleryRow.MonthHeader -> label
    is GalleryRow.Flower -> formatMonthLabel(flower.createdAt)
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
