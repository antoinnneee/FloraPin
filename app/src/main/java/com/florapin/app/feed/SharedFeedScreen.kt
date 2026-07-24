package com.florapin.app.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.data.SavedFlowerEntity
import com.florapin.app.data.imageModel
import com.florapin.app.detail.CommentsBottomSheet
import com.florapin.app.geo.PlaceNameResolver
import com.florapin.app.likes.LikeButton
import com.florapin.app.notifications.NotificationBell
import com.florapin.app.network.dto.fullPhotoUrls
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.app.ui.components.EmptyState
import com.florapin.app.ui.components.NetworkErrorState
import com.florapin.app.ui.components.PhotoCarousel
import com.florapin.app.ui.layout.topBarHeight

/**
 * Feed des fleurs partagées avec moi (NODE-72) : photo (URL présignée via Coil),
 * espèce, ami partageur, et coordonnées si le partage inclut le GPS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedFeedScreen(
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (String) -> Unit = {},
    viewModel: SharedFeedViewModel = viewModel(
        factory = SharedFeedViewModel.factory(androidx.compose.ui.platform.LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Fleur dont le fil de commentaires est ouvert (en bottom sheet), ou null.
    var commentsFor by remember { mutableStateOf<String?>(null) }

    commentsFor?.let { flowerId ->
        CommentsBottomSheet(
            flowerServerId = flowerId,
            onDismiss = {
                commentsFor = null
                viewModel.refresh()
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                expandedHeight = topBarHeight,
                title = { Text("Partag\u00E9es") },
                actions = {
                    NotificationBell(onOpen = onOpenNotifications)
                },
            )
        },
    ) { innerPadding ->
        // Tirer vers le bas recharge la première page du feed (TÂCHE 1.3). En mode
        // « Ma sélection » (favoris locaux), le tirage n'a rien à recharger.
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { if (!state.showSelectionOnly) viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Écran vide du feed uniquement hors mode sélection (la sélection a son
            // propre message quand elle est vide).
            if (!state.showSelectionOnly && !state.loading && state.items.isEmpty()) {
                // Enveloppé dans une LazyColumn pleine zone pour que le tirage
                // reste déclenchable même sans liste à faire défiler.
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize()) {
                            val error = state.error
                            if (error != null) {
                                // Erreur réseau humaine (TÂCHE 6.16) : distingue
                                // hors-ligne / serveur injoignable + bouton « Réessayer ».
                                NetworkErrorState(
                                    info = error,
                                    onRetry = { viewModel.load() },
                                )
                            } else {
                                EmptyState(
                                    title = "Rien de partag\u00E9",
                                    message = "Aucune fleur partag\u00E9e avec vous pour l'instant.",
                                )
                            }
                        }
                    }
                }
                return@PullToRefreshBox
            }

            val listState = rememberLazyStaggeredGridState()
            // Regroupement en lot (TÂCHE 3.6) : dérivé de la liste plate à chaque
            // changement. Un lot coupé entre deux pages se recompose ici dès que la
            // page suivante est fusionnée (fusion par clé de lot, pas par position).
            val rows = remember(state.items) { groupFeed(state.items) }
            // Séparateur « nouveautés » (TÂCHE 3.2) traduit en index de LIGNE.
            val separatorRow = remember(rows, state.newSeparatorIndex) {
                separatorRowIndex(state.items, rows, state.newSeparatorIndex)
            }
            // Lots dépliés (par clé) : l'utilisateur a tapé la carte-lot pour l'ouvrir.
            val expandedBatches = remember { mutableStateListOf<String>() }

            // Déclenche le chargement de la page suivante quand on approche du bas
            // (pagination keyset, TÂCHE 1.2). La garde loadMore() évite les doublons.
            val shouldLoadMore by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
                }
            }
            // Pagination réservée au feed (jamais en mode sélection : liste locale).
            LaunchedEffect(shouldLoadMore, state.items.size, state.showSelectionOnly) {
                if (shouldLoadMore && !state.showSelectionOnly) viewModel.loadMore()
            }

            // Feed en 2 colonnes (TÂCHE 3.12) : grille décalée pour un rendu type
            // mosaïque (hauteurs de cartes variables). Les éléments transversaux
            // (barre de filtres, cartes-lot 3.6, séparateurs 3.2, indicateur de
            // pagination, sélection) restent en pleine largeur via FullLine.
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "filter-bar", span = StaggeredGridItemSpan.FullLine) {
                    FeedFilterBar(
                        sort = state.sort,
                        onSelectSort = viewModel::setSort,
                        selectionOnly = state.showSelectionOnly,
                        onToggleSelection = viewModel::setShowSelectionOnly,
                        savedCount = state.saved.size,
                    )
                }
                // Mode « Ma sélection » (TÂCHE 3.11) : favoris locaux (snapshots
                // autonomes), affichables hors-ligne / partage révoqué. Cartes en
                // pleine largeur (mise en page horizontale miniature + texte).
                if (state.showSelectionOnly) {
                    if (state.saved.isEmpty()) {
                        item(key = "selection-empty", span = StaggeredGridItemSpan.FullLine) {
                            EmptyState(
                                title = "S\u00E9lection vide",
                                message = "Enregistrez une fleur d'ami avec \u2B50 " +
                                    "pour la retrouver ici, m\u00EAme hors ligne.",
                            )
                        }
                    } else {
                        items(
                            state.saved,
                            key = { "saved:${it.serverId}" },
                            span = { StaggeredGridItemSpan.FullLine },
                        ) { saved ->
                            SavedFlowerCard(
                                saved = saved,
                                onRemove = { viewModel.removeSaved(saved.serverId) },
                            )
                        }
                    }
                    return@LazyVerticalStaggeredGrid
                }
                // Chaque ligne du feed regroupé (TÂCHE 3.6) devient un élément de la
                // grille : les fleurs seules occupent une colonne (mosaïque), les
                // cartes-lot et le séparateur « nouveautés » (TÂCHE 3.2) toute la ligne.
                rows.forEachIndexed { index, row ->
                    // Séparateur « Nouveau depuis votre dernière visite » juste avant
                    // la première ligne déjà vue (TÂCHE 3.2), en pleine largeur.
                    if (index == separatorRow) {
                        item(key = "new-separator", span = StaggeredGridItemSpan.FullLine) {
                            NewSinceLastVisitSeparator()
                        }
                    }
                    when (row) {
                        is FeedRow.Single -> item(key = row.key) {
                            SharedFlowerCard(
                                item = row.item,
                                saved = row.item.flower.id in state.savedIds,
                                onToggleSave = { viewModel.toggleSaved(row.item) },
                                onToggleLike = { viewModel.toggleLike(row.item.flower.id) },
                                onReact = { code -> viewModel.react(row.item.flower.id, code) },
                                onComment = { commentsFor = row.item.flower.id },
                                onOpenProfile = { onOpenProfile(row.item.flower.ownerId) },
                            )
                        }
                        // Carte-lot « Marie a partagé N fleurs » (TÂCHE 3.6) : le tap
                        // déplie les fleurs du lot juste en dessous, sans quitter le feed.
                        // Pleine largeur pour rester lisible avec ses miniatures.
                        is FeedRow.Batch -> item(key = row.key, span = StaggeredGridItemSpan.FullLine) {
                            val expanded = row.key in expandedBatches
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                BatchHeaderCard(
                                    row = row,
                                    expanded = expanded,
                                    onToggle = {
                                        if (expanded) expandedBatches.remove(row.key)
                                        else expandedBatches.add(row.key)
                                    },
                                )
                                if (expanded) {
                                    row.items.forEach { batchItem ->
                                        SharedFlowerCard(
                                            item = batchItem,
                                            saved = batchItem.flower.id in state.savedIds,
                                            onToggleSave = { viewModel.toggleSaved(batchItem) },
                                            onToggleLike = { viewModel.toggleLike(batchItem.flower.id) },
                                            onReact = { code -> viewModel.react(batchItem.flower.id, code) },
                                            onComment = { commentsFor = batchItem.flower.id },
                                            onOpenProfile = { onOpenProfile(batchItem.flower.ownerId) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.loadingMore) {
                    item(key = "loading-more", span = StaggeredGridItemSpan.FullLine) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

/** Sélecteur d'ordre du feed (récentes / meilleures photos, NODE-140). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortBar(selected: FeedSort, onSelect: (FeedSort) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FeedSort.entries.forEach { sort ->
            FilterChip(
                modifier = Modifier.height(32.dp),
                selected = sort == selected,
                onClick = { onSelect(sort) },
                label = {
                    Text(
                        text = sort.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

/**
 * Barre de filtres du feed (TÂCHE 3.11) : l'ordre (récentes / meilleures) quand on
 * parcourt le flux, plus une puce « Ma sélection » qui bascule vers les favoris
 * locaux. En mode sélection, l'ordre du flux n'a plus de sens : on masque ses puces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedFilterBar(
    sort: FeedSort,
    onSelectSort: (FeedSort) -> Unit,
    selectionOnly: Boolean,
    onToggleSelection: (Boolean) -> Unit,
    savedCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!selectionOnly) {
            SortBar(selected = sort, onSelect = onSelectSort)
        }
        FilterChip(
            modifier = Modifier.height(32.dp),
            selected = selectionOnly,
            onClick = { onToggleSelection(!selectionOnly) },
            label = {
                Text(
                    text = if (savedCount > 0) "\u2605 Ma sélection ($savedCount)" else "\u2605 Ma sélection",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )
    }
}

/**
 * Bouton d'enregistrement dans « Ma sélection » (TÂCHE 3.11), calqué sur
 * [LikeButton] : une étoile cliquable de taille stable. Favori PRIVÉ et LOCAL.
 */
@Composable
private fun SaveButton(
    saved: Boolean,
    onClick: () -> Unit,
    photoOverlay: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (photoOverlay) {
            MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (photoOverlay) 0.dp else 1.dp,
        modifier = Modifier.size(40.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (saved) Icons.Filled.Star else Icons.Outlined.StarOutline,
                contentDescription = if (saved) {
                    "Retirer de Ma sélection"
                } else {
                    "Ajouter à Ma sélection"
                },
                tint = if (saved) {
                    Color(0xFFFFD54F)
                } else if (photoOverlay) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Carte d'un favori local (« Ma sélection », TÂCHE 3.11) : snapshot autonome
 * affiché depuis Room (miniature en cache, espèce, ami), donc visible hors-ligne
 * et même si le partage d'origine a été révoqué. Le ⭐ retire de la sélection.
 */
@Composable
private fun SavedFlowerCard(
    saved: SavedFlowerEntity,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = saved.imageModel(),
                contentDescription = "Fleur enregistr\u00E9e",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = saved.species?.takeIf { it.isNotBlank() }?.let {
                        "\uD83C\uDF3F $it"
                    } ?: "\uD83C\uDF38 Fleur enregistr\u00E9e",
                    style = MaterialTheme.typography.bodyLarge,
                )
                saved.ownerName?.let {
                    Text("Partag\u00E9e par $it", style = MaterialTheme.typography.bodySmall)
                }
                val lat = saved.latitude
                val lng = saved.longitude
                if (lat != null && lng != null) {
                    SharedLocationText(latitude = lat, longitude = lng)
                }
            }
            SaveButton(saved = true, onClick = onRemove)
        }
    }
}

/**
 * Séparateur « Nouveau depuis votre dernière visite » (TÂCHE 3.2) : un libellé
 * discret encadré de deux filets, inséré avant la première fleur déjà vue.
 */
@Composable
private fun NewSinceLastVisitSeparator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = "Nouveau depuis votre derni\u00E8re visite",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/**
 * Carte-lot repliée (TÂCHE 3.6) : « Marie a partagé N fleurs » avec un aperçu de
 * quelques miniatures. Un tap déplie (ou replie) les fleurs du lot juste en dessous.
 */
@Composable
private fun BatchHeaderCard(
    row: FeedRow.Batch,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val count = row.items.size
    val who = row.ownerName ?: "Un ami"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Aperçu : jusqu'à 3 miniatures des premières fleurs du lot.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.items.take(3).forEach { item ->
                    AsyncImage(
                        model = item.flower.previewPhotoUrls().firstOrNull(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$who a partag\u00E9 $count fleurs",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = if (expanded) {
                        "Toucher pour r\u00E9duire"
                    } else {
                        "Toucher pour ouvrir le lot"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = if (expanded) "\u25B2" else "\u25BC",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun SharedFlowerCard(
    item: SharedFlowerItem,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onToggleLike: () -> Unit,
    onReact: (String) -> Unit,
    onComment: () -> Unit,
    onOpenProfile: () -> Unit = {},
) {
    val flower = item.flower
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Box {
                // Carrousel de toutes les photos de la fleur (preview léger), avec
                // visionneuse plein écran + zoom au clic. Repli sur la couverture seule.
                PhotoCarousel(
                    previewModels = flower.previewPhotoUrls(),
                    fullModels = flower.fullPhotoUrls(),
                    contentDescription = "Fleur partag\u00E9e",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
                flower.species?.takeIf { it.isNotBlank() }?.let { species ->
                    // Même traitement que sur l'accueil : le nom est posé en bas
                    // de la photo sur un dégradé qui garantit sa lisibilité.
                    Text(
                        text = species,
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
                            .padding(
                                start = 12.dp,
                                top = 28.dp,
                                end = 12.dp,
                                bottom = 10.dp,
                            ),
                    )
                }
                // Les deux actions utilisent la même cible tactile et une surface
                // translucide discrète, à l'écart du nom de la plante.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LikeButton(
                        myReaction = flower.myReaction,
                        count = flower.likeCount,
                        onToggle = onToggleLike,
                        onReact = onReact,
                        showCount = false,
                    )
                    SaveButton(
                        saved = saved,
                        onClick = onToggleSave,
                        photoOverlay = true,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    bottom = 4.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.ownerName?.let {
                    // Le nom seul, aligné à droite, reste un accès au profil.
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable(onClick = onOpenProfile),
                    )
                }
                val lat = flower.latitude
                val lng = flower.longitude
                if (lat != null && lng != null) {
                    SharedLocationText(latitude = lat, longitude = lng)
                } else {
                    Text(
                        text = "\uD83D\uDCCD Sans position",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    onClick = onComment,
                    modifier = Modifier.height(40.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) {
                    Text(commentButtonLabel(flower.commentCount))
                }
            }
        }
    }
}

internal fun commentButtonLabel(commentCount: Int): String =
    "\uD83D\uDCAC Commenter (${commentCount.coerceAtLeast(0)})"

@Composable
private fun SharedLocationText(latitude: Double, longitude: Double) {
    val context = LocalContext.current
    var placeName by remember(context, latitude, longitude) {
        mutableStateOf("Localisation")
    }
    LaunchedEffect(context, latitude, longitude) {
        placeName = PlaceNameResolver.resolve(context, latitude, longitude) ?: "Lieu partage"
    }
    Text(
        text = "\uD83D\uDCCD $placeName",
        style = MaterialTheme.typography.bodySmall,
    )
}
