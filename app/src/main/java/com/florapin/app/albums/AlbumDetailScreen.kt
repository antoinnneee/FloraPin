package com.florapin.app.albums

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.R
import com.florapin.app.data.AlbumEntity
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.thumbnailModel
import com.florapin.app.ui.components.BotanicalIcon
import com.florapin.app.util.formatCaptureDate

/**
 * Détail d'un album traité comme une planche d'herbier photographique. La prise
 * de vue est une action de premier rang : elle ouvre la caméra avec l'album
 * courant comme destination, sans étape de classement supplémentaire.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    onFlowerClick: (Long) -> Unit,
    onCaptureInAlbum: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = viewModel(),
    collaborationViewModel: AlbumCollaborationViewModel = viewModel(),
) {
    viewModel.setAlbumId(albumId)
    val album by viewModel.album.collectAsStateWithLifecycle()
    val flowers by viewModel.flowers.collectAsStateWithLifecycle()
    var showCollaboration by remember { mutableStateOf(false) }
    var flowerActions by remember { mutableStateOf<FlowerEntity?>(null) }
    var flowerPendingRemoval by remember { mutableStateOf<FlowerEntity?>(null) }

    LaunchedEffect(album?.id, album?.groupId, album?.name) {
        album?.let { collaborationViewModel.load(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = album?.name ?: "Album",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BotanicalIcon(R.drawable.ic_back_botanical, "Retour")
                    }
                },
                actions = {
                    album?.let { current ->
                        if (current.canEdit || current.serverId != null) {
                            IconButton(onClick = { showCollaboration = true }) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Ouvrir les réglages de l'album",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (album?.canEdit != false) {
                ExtendedFloatingActionButton(
                    onClick = onCaptureInAlbum,
                    icon = {
                        BotanicalIcon(
                            R.drawable.ic_photo_botanical,
                            contentDescription = null,
                            size = 22.dp,
                        )
                    },
                    text = { Text("Photographier ici") },
                )
            }
        },
    ) { innerPadding ->
        AlbumGrid(
            album = album,
            flowers = flowers,
            onCapture = onCaptureInAlbum,
            onFlowerClick = onFlowerClick,
            onFlowerLongClick = { flower ->
                if (album?.canEdit != false) flowerActions = flower
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }

    if (showCollaboration) {
        album?.let { current ->
            val collabState by collaborationViewModel.state.collectAsStateWithLifecycle()
            AlbumCollaborationPanel(
                album = current,
                isOwner = if (collabState.group == null) true else collabState.isOwner,
                viewModel = collaborationViewModel,
                onRename = viewModel::rename,
                onDismiss = { showCollaboration = false },
            )
        }
    }

    flowerActions?.let { flower ->
        val isCurrentCover = album?.coverFlowerId == flower.id
        AlertDialog(
            onDismissRequest = { flowerActions = null },
            title = { Text("Options de la photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (isCurrentCover) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BotanicalIcon(
                                    R.drawable.ic_cover_botanical,
                                    contentDescription = null,
                                    size = 22.dp,
                                )
                                Text("Couverture actuelle")
                            }
                        }
                    } else {
                        TextButton(
                            onClick = {
                                viewModel.setCover(flower.id)
                                flowerActions = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            BotanicalIcon(
                                R.drawable.ic_cover_botanical,
                                contentDescription = null,
                                size = 22.dp,
                            )
                            Text(
                                "Utiliser comme couverture",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            flowerActions = null
                            flowerPendingRemoval = flower
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Retirer de l'album",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { flowerActions = null }) { Text("Fermer") }
            },
        )
    }

    flowerPendingRemoval?.let { flower ->
        AlertDialog(
            onDismissRequest = { flowerPendingRemoval = null },
            title = { Text("Retirer cette observation ?") },
            text = {
                Text("Elle restera dans votre collection, mais ne figurera plus dans cet album.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeFlower(flower.id)
                        flowerPendingRemoval = null
                    },
                ) { Text("Retirer") }
            },
            dismissButton = {
                TextButton(onClick = { flowerPendingRemoval = null }) { Text("Conserver") }
            },
        )
    }
}

@Composable
private fun AlbumGrid(
    album: AlbumEntity?,
    flowers: List<FlowerEntity>,
    onCapture: () -> Unit,
    onFlowerClick: (Long) -> Unit,
    onFlowerLongClick: (FlowerEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            AlbumHero(album = album, flowers = flowers)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Observations", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        if (flowers.isEmpty()) {
                            "Cet album attend sa première découverte."
                        } else {
                            "Appui long pour choisir la couverture ou retirer une photo."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (flowers.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyAlbumInvitation(
                    canEdit = album?.canEdit != false,
                    onCapture = onCapture,
                )
            }
        } else {
            itemsIndexed(flowers, key = { _, flower -> flower.id }) { index, flower ->
                AlbumFlowerThumbnail(
                    flower = flower,
                    tall = index % 3 == 0,
                    isCover = album?.coverFlowerId == flower.id,
                    onClick = { onFlowerClick(flower.id) },
                    onLongClick = { onFlowerLongClick(flower) },
                )
            }
        }
    }
}

@Composable
private fun AlbumHero(album: AlbumEntity?, flowers: List<FlowerEntity>) {
    val coverFlower = flowers.firstOrNull { it.id == album?.coverFlowerId }
        ?: flowers.firstOrNull()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(248.dp)
            .padding(top = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box {
            coverFlower?.let { flower ->
                AsyncImage(
                    model = flower.thumbnailModel(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } ?: Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                BotanicalIcon(
                    R.drawable.ic_album_option_04_leaf,
                    contentDescription = null,
                    size = 88.dp,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.04f),
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f),
                        ),
                    ),
            )
            album?.groupId?.let {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BotanicalIcon(
                            R.drawable.ic_friends_botanical,
                            contentDescription = null,
                            size = 16.dp,
                        )
                        Text("Album collaboratif", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
            ) {
                Text(
                    text = album?.name ?: "Album",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (flowers.size) {
                        0 -> "Aucune observation pour le moment"
                        1 -> "1 observation"
                        else -> "${flowers.size} observations"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun EmptyAlbumInvitation(
    canEdit: Boolean,
    onCapture: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotanicalIcon(
                R.drawable.ic_photo_botanical,
                contentDescription = null,
                size = 48.dp,
            )
            Text(
                if (canEdit) "Commencez sur le terrain" else "Album encore vide",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                if (canEdit) {
                    "La prochaine photo sera classée ici automatiquement."
                } else {
                    "Un membre autorisé peut y ajouter des observations."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (canEdit) {
                TextButton(onClick = onCapture) { Text("Prendre une photo") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumFlowerThumbnail(
    flower: FlowerEntity,
    tall: Boolean,
    isCover: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Box {
            AsyncImage(
                model = flower.thumbnailModel(),
                contentDescription = "Fleur du ${formatCaptureDate(flower.createdAt)}",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (tall) 0.82f else 1f),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        ),
                    ),
            )
            Text(
                text = formatCaptureDate(flower.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            )
            if (isCover) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                ) {
                    BotanicalIcon(
                        R.drawable.ic_cover_botanical,
                        contentDescription = "Photo de couverture",
                        modifier = Modifier.padding(6.dp),
                        size = 20.dp,
                    )
                }
            }
        }
    }
}
