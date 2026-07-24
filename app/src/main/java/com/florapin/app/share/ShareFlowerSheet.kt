package com.florapin.app.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.ShareDto
import com.florapin.app.ui.components.rememberSingleLineKeyboardActions
import com.florapin.app.ui.components.singleLineKeyboardOptions

/** Nom affiché d'un ami, avec repli sur son email. */
private fun FriendUserDto.label(): String = displayName.ifBlank { email }

/**
 * Feuille de partage de la fleur affichée : choix du destinataire (un ami ou
 * tous ses amis), inclusion du GPS, création ; liste des partages qui exposent
 * cette fleur, avec révocation.
 *
 * @param flowerServerId id serveur de la fleur, ou null si elle n'est pas encore
 *   synchronisée — le partage est alors impossible.
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
    val context = LocalContext.current
    val prefs = remember(context) { SharePreferences(context) }

    var selectedFriend by remember { mutableStateOf<FriendUserDto?>(null) }
    // Partager avec tous les amis acceptés en une fois (exclusif d'un ami précis).
    var allFriends by remember {
        mutableStateOf(prefs.defaultRecipient() == DefaultRecipient.ALL_FRIENDS)
    }
    var includeGps by remember { mutableStateOf(prefs.includeGps()) }

    // Partages qui exposent *cette* fleur : ceux qui la visent directement, plus
    // les partages « toutes mes fleurs » hérités (l'app n'en crée plus, mais ils
    // doivent rester révocables ici puisqu'ils rendent la photo visible).
    val shares = state.shares.filter {
        (it.scope == "flower" && it.flowerId == flowerServerId) || it.scope == "all"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Partager cette photo", style = MaterialTheme.typography.titleLarge)

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (flowerServerId == null) {
                Text(
                    "Cette fleur n'est pas encore synchronisée : elle ne peut pas être partagée.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    val flowerId = flowerServerId ?: return@Button
                    if (allFriends) {
                        viewModel.createShareForAll(includeGps = includeGps, flowerId = flowerId)
                    } else {
                        selectedFriend?.let { friend ->
                            viewModel.createShare(
                                friendId = friend.id,
                                includeGps = includeGps,
                                flowerId = flowerId,
                            )
                        }
                    }
                },
                enabled = flowerServerId != null && (allFriends || selectedFriend != null),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (allFriends) "Partager avec tous mes amis" else "Partager")
            }

            if (shares.isNotEmpty()) {
                HorizontalDivider()
                Text("Partages existants", style = MaterialTheme.typography.titleMedium)
                shares.forEach { share ->
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

/**
 * Libellé lisible d'un périmètre. Seul 'flower' est créé désormais ; 'all'
 * subsiste pour les partages hérités, signalés comme tels.
 */
private fun scopeLabel(scope: String): String = when (scope) {
    "all" -> "Toutes mes fleurs (hérité)"
    else -> "Cette photo"
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
        friends.firstOrNull { it.id == share.sharedWith }?.label() ?: "Ami"
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
 * Sélecteur de destinataire : « Tous mes amis », puis les derniers amis avec qui
 * l'utilisateur a partagé (au plus [SharePreferences.RECENT_LIMIT], la liste
 * arrivant déjà triée par récence). Les autres amis restent atteignables via le
 * bouton « … », qui ouvre une recherche.
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
    var picking by remember { mutableStateOf(false) }

    Text("Partager avec", style = MaterialTheme.typography.titleMedium)
    if (friends.isEmpty()) {
        Text(
            "Aucun ami accepté",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    // Un ami choisi depuis la recherche reste visible même s'il n'est pas dans
    // les raccourcis : sans cela, la sélection courante disparaîtrait de l'écran.
    val shortcuts = friends.take(SharePreferences.RECENT_LIMIT)
    val shown = if (selectedFriend != null && selectedFriend !in shortcuts) {
        shortcuts + selectedFriend
    } else {
        shortcuts
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = allSelected,
            onClick = onSelectAll,
            label = { Text("👥 Tous mes amis") },
        )
        shown.forEach { friend ->
            FilterChip(
                selected = !allSelected && selectedFriend?.id == friend.id,
                onClick = { onSelectFriend(friend) },
                label = { Text(friend.label()) },
            )
        }
        if (friends.size > shortcuts.size) {
            FilterChip(
                selected = false,
                onClick = { picking = true },
                label = { Text("…") },
                modifier = Modifier.semantics {
                    contentDescription = "Chercher un ami"
                },
            )
        }
    }

    if (picking) {
        FriendSearchDialog(
            friends = friends,
            onSelect = {
                onSelectFriend(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Recherche d'un ami par nom ou email, parmi tous les amis acceptés. */
@Composable
private fun FriendSearchDialog(
    friends: List<FriendUserDto>,
    onSelect: (FriendUserDto) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query, friends) {
        val q = query.trim()
        if (q.isBlank()) {
            friends
        } else {
            friends.filter {
                it.displayName.contains(q, ignoreCase = true) ||
                    it.email.contains(q, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chercher un ami") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    keyboardOptions = singleLineKeyboardOptions(),
                    keyboardActions = rememberSingleLineKeyboardActions(),
                    label = { Text("Nom ou email") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (matches.isEmpty()) {
                    Text(
                        "Aucun ami ne correspond.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(matches, key = { it.id }) { friend ->
                            TextButton(
                                onClick = { onSelect(friend) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(friend.label(), modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
