package com.florapin.app.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.ui.components.EmojiIcon

/**
 * Écran de gestion des amis (NODE-70) : demandes reçues/envoyées, liste d'amis,
 * invitation par identifiant utilisateur. La déconnexion a été déplacée vers
 * l'écran Profil (NODE-97).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (String) -> Unit = {},
    viewModel: FriendsViewModel = viewModel(
        factory = FriendsViewModel.factory(androidx.compose.ui.platform.LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var invitee by remember { mutableStateOf("") }
    var showQrSheet by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }

    // Scan plein écran : remplace la liste tant qu'il est actif.
    if (scanning) {
        QrScanScreen(
            onScanned = { payload ->
                viewModel.addByScan(payload)
                scanning = false
            },
            onBack = { scanning = false },
            modifier = modifier,
        )
        return
    }

    if (showQrSheet) {
        val selfId = viewModel.selfUserId
        if (selfId != null) {
            QrCodeSheet(
                userId = selfId,
                displayName = viewModel.selfDisplayName,
                onDismiss = { showQrSheet = false },
            )
        } else {
            // Identité locale inconnue (rare) : rien à afficher, on referme.
            showQrSheet = false
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Amis") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        EmojiIcon("←", contentDescription = "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                InviteField(
                    value = invitee,
                    onValueChange = { invitee = it },
                    onInvite = {
                        viewModel.invite(invitee)
                        invitee = ""
                    },
                )
            }

            item {
                QrActions(
                    onShowMyQr = { showQrSheet = true },
                    onScan = { scanning = true },
                )
            }

            state.error?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            section("Demandes reçues", state.incoming) { friendship ->
                FriendRow(
                    friendship = friendship,
                    primaryLabel = "Accepter",
                    onPrimary = { viewModel.accept(friendship.id) },
                    secondaryLabel = "Refuser",
                    onSecondary = { viewModel.remove(friendship.id) },
                )
            }

            section("Demandes envoyées", state.outgoing) { friendship ->
                FriendRow(
                    friendship = friendship,
                    secondaryLabel = "Annuler",
                    onSecondary = { viewModel.remove(friendship.id) },
                )
            }

            section("Mes amis", state.friends) { friendship ->
                FriendRow(
                    friendship = friendship,
                    secondaryLabel = "Retirer",
                    onSecondary = { viewModel.remove(friendship.id) },
                    // Tap sur la carte d'un ami accepté → son profil (TÂCHE 5.7).
                    onClick = { onOpenProfile(friendship.user.id) },
                )
            }

            if (!state.loading &&
                state.incoming.isEmpty() &&
                state.outgoing.isEmpty() &&
                state.friends.isEmpty()
            ) {
                item {
                    Text(
                        "Aucun ami pour l'instant. Invitez quelqu'un avec son email.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Ajoute un titre de section + ses lignes, seulement si la liste est non vide. */
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<FriendshipDto>,
    row: @Composable (FriendshipDto) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    items(items, key = { it.id }) { row(it) }
}

@Composable
private fun InviteField(
    value: String,
    onValueChange: (String) -> Unit,
    onInvite: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Email de l'utilisateur à inviter") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onInvite,
            enabled = value.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Inviter")
        }
    }
}

/** Deux entrées d'ajout par QR code : afficher le sien, ou scanner celui d'un ami. */
@Composable
private fun QrActions(
    onShowMyQr: () -> Unit,
    onScan: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onShowMyQr,
            modifier = Modifier.weight(1f),
        ) {
            Text("Mon QR code")
        }
        OutlinedButton(
            onClick = onScan,
            modifier = Modifier.weight(1f),
        ) {
            Text("Scanner un QR")
        }
    }
}

@Composable
private fun FriendRow(
    friendship: FriendshipDto,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryLabel: String? = null,
    onPrimary: () -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Card(modifier = cardModifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    friendship.user.displayName.ifBlank { friendship.user.email },
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    friendship.user.email,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (primaryLabel != null) {
                Button(onClick = onPrimary) { Text(primaryLabel) }
            }
            OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}
