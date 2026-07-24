package com.florapin.app.gallery

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.R
import com.florapin.app.albums.AddToAlbumSheet
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.SyncState
import com.florapin.app.data.thumbnailModel
import com.florapin.app.notifications.NotificationBell
import com.florapin.app.ui.components.EmptyState
import com.florapin.app.ui.layout.isLandscape
import com.florapin.app.ui.layout.topBarHeight
import com.florapin.app.ui.transition.FloraSharedScope
import com.florapin.app.ui.transition.sharedFlowerImage
import com.florapin.app.util.formatCaptureDate
import com.florapin.app.util.formatMonthLabel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Galerie des fleurs capturées (NODE-9) : grille de vignettes, ou message si
 * vide. L'action photo centrale lance une nouvelle capture ; un appui sur une
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
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    // Suppression annulable (TÂCHE 6.13) : id de la fleur soft-supprimée depuis le
    // détail, transmis au retour. Déclenche le snackbar « Annuler ».
    deletedFlowerId: Long? = null,
    onDeletedFlowerHandled: () -> Unit = {},
    // Transitions partagées galerie ↔ détail (TÂCHE 6.17) : null hors navigation
    // (aperçu, tests) ⇒ vignettes affichées sans animation partagée.
    sharedScope: FloraSharedScope? = null,
    viewModel: GalleryViewModel = viewModel(),
) {
    val flowers by viewModel.flowers.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val density by viewModel.density.collectAsStateWithLifecycle()
    val identifyBadge by viewModel.identifyBadge.collectAsStateWithLifecycle()
    val friendsBadge by viewModel.friendsBadge.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncEnabled by viewModel.syncEnabled.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selectionActive = selectedIds.isNotEmpty()
    val landscape = isLandscape()

    var showAddToAlbum by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Recalcule les badges à l'affichage de la galerie (lancement + retour depuis
    // les écrans « à identifier » / amis, qui auront marqué leurs demandes vues).
    LaunchedEffect(Unit) { viewModel.refreshBadges() }

    // Snackbar « Fleur supprimée / Annuler » (TÂCHE 6.13). La fleur est déjà
    // masquée (soft-delete posé depuis le détail). On propose l'annulation le
    // temps du snackbar ; à sa fermeture sans annulation, on finalise (purge ou
    // propagation). On ne consomme l'événement (mise à null) qu'APRÈS résolution
    // du snackbar, sinon le changement de clé annulerait l'attente en cours.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(deletedFlowerId) {
        val id = deletedFlowerId ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Fleur supprimée",
            actionLabel = "Annuler",
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete(id)
        } else {
            viewModel.finalizeDelete(id)
        }
        onDeletedFlowerHandled()
    }

    // En mode sélection, le retour arrière annule la sélection plutôt que de quitter.
    BackHandler(enabled = selectionActive) { viewModel.clearSelection() }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    expandedHeight = topBarHeight,
                    title = {
                        Row(
                            modifier = Modifier.offset(x = (-12).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo_florapin),
                                contentDescription = null,
                                modifier = Modifier.size(if (landscape) 44.dp else 56.dp),
                            )
                            Text(
                                text = "FloraPin",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    actions = {
                        // Topbar allégée : seules les entrées « à notifier » restent
                        // ici (identification demandée, invitations d'amis) plus la
                        // cloche du centre de notifications (TÂCHE 2.7). Le tri est
                        // descendu dans la vue, les albums dans la barre du bas.
                        BadgedIconAction(
                            icon = R.drawable.ic_identify_botanical,
                            contentDescription = "Identifications demandées",
                            badge = identifyBadge,
                            onClick = onOpenIdentify,
                        )
                        BadgedIconAction(
                            icon = R.drawable.ic_friends_botanical,
                            contentDescription = "Amis",
                            badge = friendsBadge,
                            onClick = onOpenFriends,
                        )
                        NotificationBell(
                            onOpen = onOpenNotifications,
                            modifier = Modifier.headerUtilitySurface(),
                        )
                    },
                )
            }
        },
    ) { innerPadding ->
        // Regroupement par mois (TÂCHE 6.7) : n'a de sens que quand la liste est
        // triée par date. Pour le tri par espèce, on reste en grille à plat.
        val grouped = sort == GallerySort.DATE_DESC || sort == GallerySort.DATE_ASC
        val rows = remember(flowers, grouped) {
            if (grouped) {
                flowers.groupedByMonth()
            } else {
                flowers.map { GalleryRow.Flower(it, "") }
            }
        }
        val gridState = rememberLazyGridState()

        val galleryPane: @Composable (Modifier) -> Unit = { paneModifier ->
            // Tirer vers le bas relance une passe de sync (si activée) et rafraîchit
            // les badges — la grille elle-même vient de Room (déjà à jour), device-first.
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = paneModifier,
            ) {
                when {
                    flowers.isNotEmpty() -> {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = density.minCellSize),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(if (landscape) 8.dp else 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(
                                if (landscape) 8.dp else 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(
                                if (landscape) 8.dp else 12.dp,
                            ),
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
                                            // Badge « en attente » seulement si la
                                            // sync auto est active (device-first).
                                            syncEnabled = syncEnabled,
                                            sharedScope = sharedScope,
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

        if (landscape) {
            // Composition paysage dédiée : les commandes vivent dans un panneau
            // latéral et toute la hauteur restante appartient aux grandes cartes.
            Row(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .padding(top = 4.dp),
                ) {
                    GallerySearchRow(
                        query = query,
                        onQueryChange = viewModel::setQuery,
                        selectedSort = sort,
                        selectedDensity = density,
                        onSelectSort = viewModel::setSort,
                        onSelectDensity = viewModel::setDensity,
                        onOpenMap = onOpenMap,
                        compact = true,
                    )
                }
                VerticalDivider()
                galleryPane(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            // Composition portrait : commandes empilées au-dessus de la galerie.
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
            ) {
                GallerySearchRow(
                    query = query,
                    onQueryChange = viewModel::setQuery,
                    selectedSort = sort,
                    selectedDensity = density,
                    onSelectSort = viewModel::setSort,
                    onSelectDensity = viewModel::setDensity,
                    onOpenMap = onOpenMap,
                )
                galleryPane(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
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
        expandedHeight = topBarHeight,
        navigationIcon = {
            IconButton(onClick = onClose) {
                SelectionActionIcon(
                    icon = R.drawable.ic_close_botanical,
                    contentDescription = "Quitter la sélection",
                )
            }
        },
        title = { Text("$count sélectionnée${if (count > 1) "s" else ""}") },
        actions = {
            IconButton(onClick = onSelectAll) {
                SelectionActionIcon(
                    icon = R.drawable.ic_select_all_botanical,
                    contentDescription = "Tout sélectionner",
                )
            }
            IconButton(onClick = onAddToAlbum) {
                SelectionActionIcon(
                    icon = R.drawable.ic_album_add_botanical,
                    contentDescription = "Ajouter à un album",
                )
            }
            IconButton(onClick = onDelete) {
                SelectionActionIcon(
                    icon = R.drawable.ic_delete_botanical,
                    contentDescription = "Supprimer",
                )
            }
        },
    )
}

@Composable
private fun SelectionActionIcon(
    @DrawableRes icon: Int,
    contentDescription: String,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = Modifier.size(28.dp),
        tint = Color.Unspecified,
    )
}

/** Action permanente de l'en-tête, avec badge de nouveautés. */
@Composable
private fun BadgedIconAction(
    @DrawableRes icon: Int,
    contentDescription: String,
    badge: Int,
    onClick: () -> Unit,
) {
    BadgedBox(
        badge = {
            if (badge > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                    Text(if (badge > 99) "99+" else "$badge")
                }
            }
        },
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.headerUtilitySurface(),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                modifier = Modifier.size(28.dp),
                tint = Color.Unspecified,
            )
        }
    }
}

/**
 * Conteneur discret des trois utilitaires de l'en-tête. Le bouton conserve une
 * cible tactile de 48 dp, tandis que la surface visible reste légère.
 */
@Composable
private fun Modifier.headerUtilitySurface(): Modifier {
    val shape = RoundedCornerShape(14.dp)
    return this
        .size(48.dp)
        .padding(2.dp)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
}

/**
 * Recherche photo-first : le filtre vit dans le champ et la carte garde un
 * accès direct juste à droite, comme dans la maquette retenue.
 */
@Composable
private fun GallerySearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedSort: GallerySort,
    selectedDensity: GalleryDensity,
    onSelectSort: (GallerySort) -> Unit,
    onSelectDensity: (GalleryDensity) -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = if (compact) 2.dp else 8.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            selectedSort = selectedSort,
            selectedDensity = selectedDensity,
            onSelectSort = onSelectSort,
            onSelectDensity = onSelectDensity,
            modifier = Modifier.weight(1f),
        )

        val mapShape = RoundedCornerShape(16.dp)
        IconButton(
            onClick = onOpenMap,
            modifier = Modifier
                .size(if (compact) 52.dp else 56.dp)
                .clip(mapShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, mapShape),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_nav_map),
                contentDescription = "Ouvrir la carte",
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Barre de recherche par espèce, notes ou étiquette (NODE-120). */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    selectedSort: GallerySort,
    selectedDensity: GalleryDensity,
    onSelectSort: (GallerySort) -> Unit,
    onSelectDensity: (GalleryDensity) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_identify_botanical),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = Color.Unspecified,
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close_botanical),
                            contentDescription = "Effacer la recherche",
                            modifier = Modifier.size(22.dp),
                            tint = Color.Unspecified,
                        )
                    }
                }
                GalleryFilterAction(
                    selectedSort = selectedSort,
                    selectedDensity = selectedDensity,
                    onSelectSort = onSelectSort,
                    onSelectDensity = onSelectDensity,
                )
            }
        },
        placeholder = { Text("Rechercher une plante") },
        modifier = modifier
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
    )
}

/**
 * Menu unifié : le tri et la densité restent disponibles sans prendre une
 * rangée permanente au-dessus des photos.
 */
@Composable
private fun GalleryFilterAction(
    selectedSort: GallerySort,
    selectedDensity: GalleryDensity,
    onSelectSort: (GallerySort) -> Unit,
    onSelectDensity: (GalleryDensity) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = "Trier et régler la densité",
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Text(
                text = "Trier par",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            GallerySort.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    leadingIcon = {
                        RadioButton(
                            selected = order == selectedSort,
                            onClick = null,
                        )
                    },
                    onClick = {
                        onSelectSort(order)
                        expanded = false
                    },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "Densité",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            GalleryDensity.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    leadingIcon = {
                        RadioButton(
                            selected = option == selectedDensity,
                            onClick = null,
                        )
                    },
                    onClick = {
                        onSelectDensity(option)
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
        actionLabel = onCapture?.let { "Capturer une fleur" },
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
    syncEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    sharedScope: FloraSharedScope? = null,
) {
    // Nom de l'espèce si disponible (commun → scientifique → saisie libre),
    // sinon on retombe sur la date de capture.
    val name = flower.displayName()
    val cardShape = RoundedCornerShape(18.dp)
    val cardModifier = Modifier
        .fillMaxWidth()
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .then(
            // Liseré primaire quand la vignette est sélectionnée.
            if (selected) {
                Modifier.border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = cardShape,
                )
            } else {
                Modifier
            },
        )
    Card(
        modifier = cardModifier,
        shape = cardShape,
    ) {
        Box {
                AsyncImage(
                    model = flower.thumbnailModel(),
                    contentDescription = name ?: "Fleur du ${formatCaptureDate(flower.createdAt)}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.82f)
                        // Élément partagé vers l'image du détail (TÂCHE 6.17) ;
                        // no-op sans portée de transition.
                        .sharedFlowerImage(sharedScope, flower.id),
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
                // Badge discret d'état de sync (TÂCHE 6.14), en haut à gauche : les
                // fleurs non synchronisées (en attente d'envoi ou en échec) quand la
                // sync auto est active. Masqué en mode sélection pour ne pas surcharger.
                if (!selected) {
                    flower.syncBadge(syncEnabled)?.let { badge ->
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                    }
                }
            Text(
                text = name ?: formatCaptureDate(flower.createdAt),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.72f),
                            ),
                        ),
                    )
                    .padding(start = 12.dp, top = 28.dp, end = 12.dp, bottom = 10.dp),
            )
        }
    }
}

/**
 * Emoji d'état de synchronisation à surimprimer sur une vignette (TÂCHE 6.14), ou
 * null si rien à signaler. Device-first : on ne signale l'attente que si la sync
 * auto est active ([syncEnabled]) — une fleur PENDING sync OFF est l'état de repos
 * normal. « ☁️ » = en attente d'envoi ; « ⚠️ » = dernier envoi en échec.
 */
private fun FlowerEntity.syncBadge(syncEnabled: Boolean): String? {
    if (!syncEnabled) return null
    return when (syncState) {
        SyncState.PENDING.name -> "☁️"
        SyncState.FAILED.name -> "⚠️"
        else -> null
    }
}

/** Nom d'espèce affichable (commun, scientifique ou saisie libre), ou null. */
private fun FlowerEntity.displayName(): String? =
    speciesCommonName?.takeIf { it.isNotBlank() }
        ?: speciesScientificName?.takeIf { it.isNotBlank() }
        ?: species?.takeIf { it.isNotBlank() }
