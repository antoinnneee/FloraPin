package com.florapin.app.albums

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.florapin.app.data.AlbumEntity
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.network.dto.GroupMemberDto
import com.florapin.app.ui.components.rememberSingleLineKeyboardActions
import com.florapin.app.ui.components.singleLineKeyboardOptions

/**
 * Réglages d'un album : son titre et, quand il est collaboratif, ses membres et
 * leurs droits. La feuille conserve toutes les décisions qui concernent l'album
 * au même endroit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCollaborationPanel(
    album: AlbumEntity,
    isOwner: Boolean,
    viewModel: AlbumCollaborationViewModel,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var titleDraft by remember(album.id) { mutableStateOf(album.name) }
    val trimmedTitle = titleDraft.trim()
    val titleChanged = trimmedTitle.isNotEmpty() && trimmedTitle != album.name

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Réglages de l'album",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Titre, accès et membres",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Fermer")
                }
            }

            AlbumTitleSection(
                title = titleDraft,
                canEdit = album.canEdit,
                changed = titleChanged,
                onTitleChange = { titleDraft = it },
                onSave = { onRename(trimmedTitle) },
            )

            state.error?.let { error ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            val group = state.group
            when {
                group == null && album.groupId == null -> {
                    PrivateAlbumSection(
                        isOwner = isOwner,
                        loading = state.loading,
                        onMakeCollaborative = viewModel::makeCollaborative,
                    )
                }

                group == null && state.loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }

                group != null -> {
                    if (state.isOwner) {
                        PermissionsSection(
                            selectedMode = state.permissionMode,
                            onModeSelected = viewModel::setPermissionMode,
                        )
                    } else {
                        PermissionSummary(canEdit = album.canEdit)
                    }

                    MembersSection(
                        members = group.members,
                        ownerUserId = group.ownerId,
                        isGroupOwner = state.isOwner,
                        restricted = state.permissionMode == "restricted",
                        permissions = state.permissions,
                        onToggleEdit = viewModel::setMemberCanEdit,
                        onRemove = viewModel::removeMember,
                    )

                    if (state.isOwner && state.invitableFriends.isNotEmpty()) {
                        InviteSection(
                            friends = state.invitableFriends,
                            onInvite = viewModel::invite,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumTitleSection(
    title: String,
    canEdit: Boolean,
    changed: Boolean,
    onTitleChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(
            title = "Titre",
            description = if (canEdit) {
                "Le nom affiché sur la couverture et dans la liste des albums."
            } else {
                "Vous consultez cet album en lecture seule."
            },
        )
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = canEdit,
            singleLine = true,
            label = { Text("Nom de l'album") },
            keyboardOptions = singleLineKeyboardOptions(),
            keyboardActions = rememberSingleLineKeyboardActions(),
        )
        if (canEdit) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onSave,
                    enabled = changed,
                ) {
                    Text("Enregistrer le titre")
                }
            }
        }
    }
}

@Composable
private fun PrivateAlbumSection(
    isOwner: Boolean,
    loading: Boolean,
    onMakeCollaborative: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeading(
            title = "Accès",
            description = "Cet album est privé : vous seul pouvez le consulter et le modifier.",
        )
        if (isOwner) {
            FilledTonalButton(
                onClick = onMakeCollaborative,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Inviter des membres")
            }
        }
    }
}

@Composable
private fun PermissionsSection(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(
            title = "Droits d'édition",
            description = "Choisissez ce que les membres peuvent faire dans cet album.",
        )
        PermissionModeCard(
            selected = selectedMode == "open",
            title = "Tous peuvent modifier",
            description = "Chaque membre peut ajouter, retirer et organiser les photos.",
            onClick = { onModeSelected("open") },
        )
        PermissionModeCard(
            selected = selectedMode == "restricted",
            title = "Choisir qui peut modifier",
            description = "Les autres membres gardent un accès en lecture seule.",
            onClick = { onModeSelected("restricted") },
        )
    }
}

@Composable
private fun PermissionModeCard(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        shape = shape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionSummary(canEdit: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = if (canEdit) "Vous pouvez modifier cet album" else "Album en lecture seule",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (canEdit) {
                    "Vous pouvez ajouter, retirer et organiser les photos."
                } else {
                    "Seuls les membres autorisés peuvent modifier son contenu."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MembersSection(
    members: List<GroupMemberDto>,
    ownerUserId: String,
    isGroupOwner: Boolean,
    restricted: Boolean,
    permissions: Map<String, Boolean>,
    onToggleEdit: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeading(
            title = "Membres",
            description = "${members.count { it.status == "accepted" }} personne" +
                if (members.count { it.status == "accepted" } > 1) "s dans l'album" else " dans l'album",
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                members.forEachIndexed { index, member ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(start = 62.dp))
                    }
                    MemberRow(
                        member = member,
                        isGroupOwner = isGroupOwner,
                        restricted = restricted,
                        canEdit = permissions[member.userId] == true,
                        ownerUserId = ownerUserId,
                        onToggleEdit = { granted -> onToggleEdit(member.userId, granted) },
                        onRemove = { onRemove(member.userId) },
                    )
                }
            }
        }
    }
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
    val isPending = member.status == "pending"
    val displayName = member.displayName.ifBlank { member.userId }
    val editStatus = when {
        isOwnerMember -> "Propriétaire · peut modifier"
        isPending -> "Invitation en attente"
        !restricted -> "Peut modifier"
        canEdit -> "Modification autorisée"
        else -> "Lecture seule"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayName.firstOrNull()?.uppercase() ?: "•",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = editStatus,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isGroupOwner && restricted && !isOwnerMember && !isPending) {
            Switch(
                checked = canEdit,
                onCheckedChange = onToggleEdit,
            )
        }
        if (isGroupOwner && !isOwnerMember) {
            TextButton(onClick = onRemove) {
                Text(
                    text = "Retirer",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun InviteSection(
    friends: List<FriendshipDto>,
    onInvite: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeading(
            title = "Inviter",
            description = "Ajoutez une personne déjà présente dans vos amis.",
        )
        friends.forEach { friendship ->
            val friend = friendship.user
            val displayName = friend.displayName.ifBlank { friend.email }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                    if (friend.email.isNotBlank() && friend.email != displayName) {
                        Text(
                            text = friend.email,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                FilledTonalButton(onClick = { onInvite(friend.id) }) {
                    Text("Inviter")
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
