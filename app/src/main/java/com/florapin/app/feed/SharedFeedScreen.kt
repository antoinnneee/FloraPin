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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.detail.CommentsBottomSheet
import com.florapin.app.likes.LikeButton
import com.florapin.app.likes.LikersBottomSheet
import com.florapin.app.notifications.NotificationBell
import com.florapin.app.network.dto.fullPhotoUrls
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.app.ui.components.EmptyState
import com.florapin.app.ui.components.PhotoCarousel

/**
 * Feed des fleurs partagées avec moi (NODE-72) : photo (URL présignée via Coil),
 * espèce, ami partageur, et coordonnées si le partage inclut le GPS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedFeedScreen(
    onOpenNotifications: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SharedFeedViewModel = viewModel(
        factory = SharedFeedViewModel.factory(androidx.compose.ui.platform.LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Fleur dont le fil de commentaires est ouvert (en bottom sheet), ou null.
    var commentsFor by remember { mutableStateOf<String?>(null) }
    // Fleur dont la liste des likers est ouverte (en bottom sheet), ou null.
    var likersFor by remember { mutableStateOf<String?>(null) }

    commentsFor?.let { flowerId ->
        CommentsBottomSheet(
            flowerServerId = flowerId,
            onDismiss = { commentsFor = null },
        )
    }

    likersFor?.let { flowerId ->
        LikersBottomSheet(
            flowerServerId = flowerId,
            onDismiss = { likersFor = null },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Partagées avec moi") },
                actions = {
                    NotificationBell(onOpen = onOpenNotifications)
                },
            )
        },
    ) { innerPadding ->
        // Tirer vers le bas recharge la première page du feed (TÂCHE 1.3).
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!state.loading && state.items.isEmpty()) {
                // Enveloppé dans une LazyColumn pleine zone pour que le tirage
                // reste déclenchable même sans liste à faire défiler.
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Box(modifier = Modifier.fillParentMaxSize()) {
                            EmptyState(
                                title = state.error?.let { "Oups" } ?: "Rien de partagé",
                                message = state.error
                                    ?: "Aucune fleur partagée avec vous pour l'instant.",
                            )
                        }
                    }
                }
                return@PullToRefreshBox
            }

            val listState = rememberLazyListState()
            // Déclenche le chargement de la page suivante quand on approche du bas
            // (pagination keyset, TÂCHE 1.2). La garde loadMore() évite les doublons.
            val shouldLoadMore by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 3
                }
            }
            LaunchedEffect(shouldLoadMore, state.items.size) {
                if (shouldLoadMore) viewModel.loadMore()
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SortBar(selected = state.sort, onSelect = viewModel::setSort)
                }
                itemsIndexed(state.items, key = { _, item -> item.flower.id }) { index, item ->
                    // Séparateur « Nouveau depuis votre dernière visite » juste avant
                    // la première fleur déjà vue (TÂCHE 3.2).
                    if (index == state.newSeparatorIndex) {
                        NewSinceLastVisitSeparator()
                    }
                    SharedFlowerCard(
                        item = item,
                        onToggleLike = { viewModel.toggleLike(item.flower.id) },
                        onReact = { code -> viewModel.react(item.flower.id, code) },
                        onOpenLikers = { likersFor = item.flower.id },
                        onComment = { commentsFor = item.flower.id },
                    )
                }
                if (state.loadingMore) {
                    item(key = "loading-more") {
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
            text = "Nouveau depuis votre dernière visite",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SharedFlowerCard(
    item: SharedFlowerItem,
    onToggleLike: () -> Unit,
    onReact: (String) -> Unit,
    onOpenLikers: () -> Unit,
    onComment: () -> Unit,
) {
    val flower = item.flower
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            // Carrousel de toutes les photos de la fleur (preview léger), avec
            // visionneuse plein écran + zoom au clic. Repli sur la couverture seule.
            PhotoCarousel(
                previewModels = flower.previewPhotoUrls(),
                fullModels = flower.fullPhotoUrls(),
                contentDescription = "Fleur partagée",
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LikeButton(
                            myReaction = flower.myReaction,
                            count = flower.likeCount,
                            onToggle = onToggleLike,
                            onReact = onReact,
                            reactionCounts = flower.reactionCounts,
                            onCountClick = onOpenLikers.takeIf { flower.likeCount > 0 },
                        )
                        // Compteur de commentaires (TÂCHE 3.3) : 💬 à côté du cœur,
                        // ouvre le fil au clic.
                        CommentCount(count = flower.commentCount, onClick = onComment)
                    }
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
                TextButton(onClick = onComment) {
                    Text("💬 Commenter")
                }
            }
        }
    }
}

/**
 * Compteur de commentaires (TÂCHE 3.3) : puce 💬 + nombre, calquée sur
 * `LikeButton`, à côté du cœur. Ouvre le fil de commentaires au clic.
 */
@Composable
private fun CommentCount(count: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text("💬")
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
