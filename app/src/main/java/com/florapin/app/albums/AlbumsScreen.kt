package com.florapin.app.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.florapin.app.ui.components.EmptyState
import com.florapin.app.ui.components.rememberSingleLineKeyboardActions
import com.florapin.app.ui.components.singleLineKeyboardOptions
import com.florapin.app.ui.layout.topBarHeight

/**
 * Bibliothèque d'albums pensée comme un herbier de terrain : les captures
 * récentes fabriquent la couverture et rendent chaque collection identifiable
 * avant même d'en lire le nom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onOpenAlbum: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: AlbumsViewModel = viewModel(),
) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AlbumEntity?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                expandedHeight = topBarHeight,
                title = { Text("Albums") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            BotanicalIcon(R.drawable.ic_back_botanical, "Retour")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = {
                    BotanicalIcon(
                        R.drawable.ic_add_botanical,
                        contentDescription = null,
                        size = 22.dp,
                    )
                },
                text = { Text("Nouvel album") },
            )
        },
    ) { innerPadding ->
        if (summaries.isEmpty()) {
            EmptyState(
                title = "Votre premier herbier",
                message = "Créez un album pour réunir vos observations par lieu, saison ou projet.",
                modifier = Modifier.padding(innerPadding),
                iconRes = R.drawable.ic_album_option_01_bookmark,
                tintIcon = false,
                actionLabel = "Créer un album",
                onAction = { showCreate = true },
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 164.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AlbumLibraryHeader(summaries)
                }
                items(summaries, key = { it.album.id }) { summary ->
                    AlbumCoverCard(
                        summary = summary,
                        onClick = { onOpenAlbum(summary.album.id) },
                        onRename = { renaming = summary.album },
                        onDelete = { viewModel.delete(summary.album) },
                    )
                }
            }
        }
    }

    if (showCreate) {
        AlbumNameDialog(
            title = "Composer un album",
            initialName = "",
            collaborativeOption = true,
            onConfirm = { name, collaborative ->
                if (collaborative) viewModel.createCollaborative(name) else viewModel.create(name)
                showCreate = false
            },
            onDismiss = { showCreate = false },
        )
    }
    renaming?.let { album ->
        AlbumNameDialog(
            title = "Renommer l'album",
            initialName = album.name,
            onConfirm = { name, _ ->
                viewModel.rename(album, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    message?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text("Collaboration indisponible") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { Text("Fermer") }
            },
        )
    }
}

@Composable
private fun AlbumLibraryHeader(summaries: List<AlbumSummary>) {
    val photoCount = summaries.sumOf { it.flowers.size }
    Column(
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Votre herbier, en histoires",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "${summaries.size} album${if (summaries.size > 1) "s" else ""} · " +
                "$photoCount observation${if (photoCount > 1) "s" else ""}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlbumCoverCard(
    summary: AlbumSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val album = summary.album
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
    ) {
        Box {
            AlbumContactSheet(summary.flowers)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.48f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
            )
            if (album.groupId != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BotanicalIcon(
                            R.drawable.ic_friends_botanical,
                            contentDescription = null,
                            size = 15.dp,
                        )
                        Text("Partagé", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Options de l'album",
                        tint = if (summary.flowers.isEmpty()) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            Color.White
                        },
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Renommer") },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Supprimer") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
            ) {
                Text(
                    text = album.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (summary.flowers.size) {
                        0 -> "Prêt à accueillir vos photos"
                        1 -> "1 observation"
                        else -> "${summary.flowers.size} observations"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.82f),
                )
            }
        }
    }
}

@Composable
private fun AlbumContactSheet(flowers: List<FlowerEntity>) {
    if (flowers.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            BotanicalIcon(
                iconRes = R.drawable.ic_album_option_04_leaf,
                contentDescription = null,
                size = 72.dp,
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = flowers.first().thumbnailModel(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (flowers.size > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 54.dp, end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                flowers.drop(1).take(2).forEach { flower ->
                    AsyncImage(
                        model = flower.thumbnailModel(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 56.dp, height = 70.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumNameDialog(
    title: String,
    initialName: String,
    onConfirm: (name: String, collaborative: Boolean) -> Unit,
    onDismiss: () -> Unit,
    collaborativeOption: Boolean = false,
) {
    var name by remember { mutableStateOf(initialName) }
    var collaborative by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de l'album") },
                    supportingText = { Text("Ex. Balades en forêt, Roses du jardin…") },
                    singleLine = true,
                    keyboardOptions = singleLineKeyboardOptions(),
                    keyboardActions = rememberSingleLineKeyboardActions(),
                )
                if (collaborativeOption) {
                    Text(
                        "Qui peut enrichir cet album ?",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !collaborative,
                            onClick = { collaborative = false },
                            label = { Text("Moi uniquement") },
                        )
                        FilterChip(
                            selected = collaborative,
                            onClick = { collaborative = true },
                            label = { Text("Avec mes amis") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, collaborative) },
                enabled = name.isNotBlank(),
            ) { Text(if (initialName.isBlank()) "Créer l'album" else "Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
