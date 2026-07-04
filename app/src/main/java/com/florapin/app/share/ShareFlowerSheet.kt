package com.florapin.app.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Surface
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
import com.florapin.app.network.dto.ShareDto

/**
 * Feuille de partage d'une fleur (NODE-71) : choix du/des destinataire(s) (un ami
 * ou tous ses amis), périmètre (cette fleur / un album / toutes), inclusion du
 * GPS, création ; liste des partages existants (avec destinataire) et révocation.
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
    // Partager avec tous les amis acceptés en une fois (exclusif d'un ami précis).
    var allFriends by remember { mutableStateOf(false) }
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

            FriendSelector(
                friends = state.friends,
                selectedFriend = selectedFriend,
                allSelected = allFriends,
                onSelectFriend = {
                    selectedFriend = it
                    allFriends = false
                },
                onSelectAll = {
                    allFriends = true
                    selectedFriend = null
                },
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

            val scopeReady = when (scope) {
                "flower" -> flowerServerId != null
                "album" -> selectedAlbum != null
                else -> true
            }
            Button(
                onClick = {
                    if (allFriends) {
                        viewModel.createShareForAll(
                            scope = scope,
                            includeGps = includeGps,
                            flowerId = flowerServerId,
                            albumId = selectedAlbum?.id,
                        )
                    } else {
                        selectedFriend?.let { friend ->
                            viewModel.createShare(
                                friendId = friend.id,
                                scope = scope,
                                includeGps = includeGps,
                                flowerId = flowerServerId,
                                albumId = selectedAlbum?.id,
                            )
                        }
                    }
                },
                enabled = (allFriends || selectedFriend != null) && scopeReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (allFriends) "Partager avec tous mes amis" else "Partager")
            }

            if (state.shares.isNotEmpty()) {
                HorizontalDivider()
                Text("Partages existants", style = MaterialTheme.typography.titleMedium)
                state.shares.forEach { share ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RecipientBadge(share = share, friends = state.friends)
                        Text(
                            text = scopeLabel(share.scope) +
                                (if (share.includeGps) " · GPS" else " · sans GPS"),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
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

/**
 * Badge du destinataire d'un partage. Un partage réseau (audience='all_friends')
 * reçoit un badge coloré distinctif « 👥 Tous mes amis » (il vaut aussi pour les
 * amis ajoutés plus tard) ; un partage ciblé affiche le nom de l'ami, résolu
 * depuis la liste des amis acceptés (libellé neutre si la relation a été retirée).
 */
@Composable
private fun RecipientBadge(share: ShareDto, friends: List<FriendUserDto>) {
    val isAll = share.audience == "all_friends"
    val label = if (isAll) {
        "👥 Tous mes amis"
    } else {
        friends.firstOrNull { it.id == share.sharedWith }
            ?.let { it.displayName.ifBlank { it.email } } ?: "Ami"
    }
    val background = if (isAll) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val foreground = if (isAll) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = background, shape = MaterialTheme.shapes.small) {
        Text(
            text = label,
            color = foreground,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Sélecteur de destinataire : liste inline (chips) des amis acceptés, avec une
 * option « Tous mes amis ». Plus visible qu'un menu déroulant caché.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FriendSelector(
    friends: List<FriendUserDto>,
    selectedFriend: FriendUserDto?,
    allSelected: Boolean,
    onSelectFriend: (FriendUserDto) -> Unit,
    onSelectAll: () -> Unit,
) {
    Text("Partager avec", style = MaterialTheme.typography.titleMedium)
    if (friends.isEmpty()) {
        Text(
            "Aucun ami accepté",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = allSelected,
            onClick = onSelectAll,
            label = { Text("👥 Tous mes amis") },
        )
        friends.forEach { friend ->
            FilterChip(
                selected = !allSelected && selectedFriend?.id == friend.id,
                onClick = { onSelectFriend(friend) },
                label = { Text(friend.displayName.ifBlank { friend.email }) },
            )
        }
    }
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
