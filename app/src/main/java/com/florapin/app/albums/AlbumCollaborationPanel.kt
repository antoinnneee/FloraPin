package com.florapin.app.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.florapin.app.data.AlbumEntity
import com.florapin.app.network.dto.GroupMemberDto

/**
 * Panneau de collaboration d'un album (TÂCHE 7.1), présenté en dialogue. Selon
 * l'état :
 * - album solo dont je suis propriétaire → bouton « Rendre collaboratif » ;
 * - album de groupe → liste des membres, invitations d'amis, régime de droits
 *   (ouvert / au cas par cas) réservé au propriétaire.
 */
@Composable
fun AlbumCollaborationPanel(
    album: AlbumEntity,
    isOwner: Boolean,
    viewModel: AlbumCollaborationViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Collaboration") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                val group = state.group
                if (group == null) {
                    if (album.groupId == null) {
                        Text(
                            "Cet album est privé. Rendez-le collaboratif pour y " +
                                "inviter des amis et construire l'album ensemble.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (isOwner) {
                            TextButton(
                                onClick = { viewModel.makeCollaborative() },
                                enabled = !state.loading,
                            ) { Text("Rendre collaboratif") }
                        }
                    } else if (state.loading) {
                        Text("Chargement…")
                    }
                } else {
                    // --- Régime de droits (propriétaire) ---
                    if (state.isOwner) {
                        Text("Droits", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.permissionMode == "open",
                                onClick = { viewModel.setPermissionMode("open") },
                                label = { Text("Tout ouvert") },
                            )
                            FilterChip(
                                selected = state.permissionMode == "restricted",
                                onClick = { viewModel.setPermissionMode("restricted") },
                                label = { Text("Au cas par cas") },
                            )
                        }
                        Divider()
                    }

                    // --- Membres ---
                    Text("Membres", fontWeight = FontWeight.SemiBold)
                    group.members.forEach { member ->
                        MemberRow(
                            member = member,
                            isGroupOwner = state.isOwner,
                            restricted = state.permissionMode == "restricted",
                            canEdit = state.permissions[member.userId] == true,
                            ownerUserId = group.ownerId,
                            onToggleEdit = { granted ->
                                viewModel.setMemberCanEdit(member.userId, granted)
                            },
                            onRemove = { viewModel.removeMember(member.userId) },
                        )
                    }

                    // --- Invitations ---
                    if (state.invitableFriends.isNotEmpty()) {
                        Divider()
                        Text("Inviter un ami", fontWeight = FontWeight.SemiBold)
                        state.invitableFriends.forEach { friend ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    friend.user.displayName.ifBlank { friend.user.email },
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { viewModel.invite(friend.user.id) }) {
                                    Text("Inviter")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun MemberRow(
    member: GroupMemberDto,
    isGroupOwner: Boolean,
    restricted: Boolean,
    canEdit: Boolean,
    ownerUserId: String,
    onToggleEdit: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val isOwnerMember = member.userId == ownerUserId
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.displayName.ifBlank { member.userId })
            val badge = when {
                isOwnerMember -> "propriétaire"
                member.status == "pending" -> "invitation en attente"
                else -> "membre"
            }
            Text(badge, style = MaterialTheme.typography.bodySmall)
        }
        // Droit d'édition individuel (mode restreint, propriétaire, hors owner).
        if (isGroupOwner && restricted && !isOwnerMember) {
            Switch(checked = canEdit, onCheckedChange = onToggleEdit)
        }
        // Retrait (propriétaire, sur un autre que lui).
        if (isGroupOwner && !isOwnerMember) {
            TextButton(onClick = onRemove) { Text("Retirer") }
        }
    }
}
