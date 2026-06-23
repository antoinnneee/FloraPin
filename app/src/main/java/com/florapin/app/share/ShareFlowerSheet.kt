package com.florapin.app.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.FriendUserDto

/**
 * Feuille de partage d'une fleur (NODE-71) : choix de l'ami, périmètre (cette
 * fleur / toutes), inclusion du GPS, création ; liste des partages existants
 * avec révocation.
 *
 * @param flowerServerId id serveur de la fleur (null si non synchronisée : le
 *   périmètre « cette fleur » est alors indisponible).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFlowerSheet(
    flowerServerId: String?,
    onDismiss: () -> Unit,
    viewModel: ShareViewModel = viewModel(
        factory = ShareViewModel.factory(LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var selectedFriend by remember { mutableStateOf<FriendUserDto?>(null) }
    var selectedAlbum by remember { mutableStateOf<AlbumDto?>(null) }
    var scope by remember { mutableStateOf(if (flowerServerId != null) "flower" else "all") }
    var includeGps by remember { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Partager", style = MaterialTheme.typography.titleLarge)

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            FriendPicker(
                friends = state.friends,
                selected = selectedFriend,
                onSelect = { selectedFriend = it },
            )

            // Périmètre du partage.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = scope == "flower",
                    enabled = flowerServerId != null,
                    onClick = { scope = "flower" },
                    label = { Text("Cette fleur") },
                )
                FilterChip(
                    selected = scope == "album",
                    enabled = state.albums.isNotEmpty(),
                    onClick = { scope = "album" },
                    label = { Text("Un album") },
                )
                FilterChip(
                    selected = scope == "all",
                    onClick = { scope = "all" },
                    label = { Text("Toutes mes fleurs") },
                )
            }

            // Sélection de l'album (périmètre 'album').
            if (scope == "album") {
                AlbumPicker(
                    albums = state.albums,
                    selected = selectedAlbum,
                    onSelect = { selectedAlbum = it },
                )
            }

            // Inclusion du GPS.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Inclure la position GPS")
                Switch(checked = includeGps, onCheckedChange = { includeGps = it })
            }

            Button(
                onClick = {
                    selectedFriend?.let { friend ->
                        viewModel.createShare(
                            friendId = friend.id,
                            scope = scope,
                            includeGps = includeGps,
                            flowerId = flowerServerId,
                            albumId = selectedAlbum?.id,
                        )
                    }
                },
                enabled = selectedFriend != null && when (scope) {
                    "flower" -> flowerServerId != null
                    "album" -> selectedAlbum != null
                    else -> true
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Partager")
            }

            if (state.shares.isNotEmpty()) {
                HorizontalDivider()
                Text("Partages existants", style = MaterialTheme.typography.titleMedium)
                state.shares.forEach { share ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = scopeLabel(share.scope) +
                                (if (share.includeGps) " · GPS" else " · sans GPS"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(onClick = { viewModel.revoke(share.id) }) {
                            Text("Révoquer")
                        }
                    }
                }
            }
        }
    }
}

/** Libellé lisible d'un périmètre de partage. */
private fun scopeLabel(scope: String): String = when (scope) {
    "flower" -> "Une fleur"
    "album" -> "Un album"
    else -> "Toutes"
}

/** Sélecteur d'album : bouton qui ouvre un menu déroulant des albums. */
@Composable
private fun AlbumPicker(
    albums: List<AlbumDto>,
    selected: AlbumDto?,
    onSelect: (AlbumDto) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = albums.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected?.name ?: "Choisir un album")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            albums.forEach { album ->
                DropdownMenuItem(
                    text = { Text(album.name) },
                    onClick = {
                        onSelect(album)
                        open = false
                    },
                )
            }
        }
    }
}

/** Sélecteur d'ami : bouton qui ouvre un menu déroulant des amis acceptés. */
@Composable
private fun FriendPicker(
    friends: List<FriendUserDto>,
    selected: FriendUserDto?,
    onSelect: (FriendUserDto) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { open = true },
            enabled = friends.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                selected?.let { it.displayName.ifBlank { it.email } }
                    ?: if (friends.isEmpty()) "Aucun ami accepté" else "Choisir un ami",
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            friends.forEach { friend ->
                DropdownMenuItem(
                    text = { Text(friend.displayName.ifBlank { friend.email }) },
                    onClick = {
                        onSelect(friend)
                        open = false
                    },
                )
            }
        }
    }
}
