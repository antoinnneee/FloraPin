package com.florapin.app.detail

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.CommentsApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.CreateCommentRequest
import com.florapin.app.network.dto.FlowerCommentDto
import com.florapin.app.network.dto.UpdateCommentRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/** État de la section « Commentaires » d'une fleur. */
data class CommentsUiState(
    val comments: List<FlowerCommentDto> = emptyList(),
    val draft: String = "",
    val loading: Boolean = false,
    val submitting: Boolean = false,
    /** Commentaire dont la suppression est en cours. */
    val deletingId: String? = null,
    /** Commentaire en cours d'édition (menu « Éditer »), `null` sinon. */
    val editingId: String? = null,
    /** Texte en cours d'édition pour [editingId]. */
    val editDraft: String = "",
    /** Édition en cours d'envoi. */
    val editSubmitting: Boolean = false,
    val error: String? = null,
)

/**
 * Fil de discussion d'une fleur : charge les commentaires
 * (`GET flowers/{id}/comments`), en poste (`POST`) et en supprime (`DELETE`).
 * Indépendant de la sync device-first : ces données collaboratives vivent côté
 * serveur. Utilisable côté propriétaire (détail) comme côté ami (feed).
 */
class CommentsViewModel(
    private val api: CommentsApi,
    private val drafts: CommentDraftStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var serverId: String? = null

    /** Associe la fleur [flowerServerId] (idempotent) et charge ses commentaires. */
    fun bind(flowerServerId: String) {
        if (serverId != flowerServerId) {
            serverId = flowerServerId
            // Nouvelle fleur : restaure le brouillon éventuellement persisté
            // (survit à la fermeture de la sheet / redémarrage de l'appli).
            _state.update { it.copy(draft = drafts.load(flowerServerId)) }
            load()
            return
        }
        // Même fleur : rechargement explicite uniquement si pas encore chargée.
        if (!_state.value.loading && _state.value.comments.isNotEmpty()) return
        load()
    }

    /** (Re)charge les commentaires de la fleur liée. */
    fun load() {
        val id = serverId ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _state.value = try {
                _state.value.copy(loading = false, comments = api.list(id), error = null)
            } catch (e: Exception) {
                // Fleur non accessible (403/404) : on masque simplement le fil.
                _state.value.copy(loading = false, comments = emptyList(), error = null)
            }
        }
    }

    /** Met à jour le brouillon du champ de saisie (et le persiste par fleur). */
    fun updateDraft(text: String) {
        _state.update { it.copy(draft = text) }
        serverId?.let { drafts.save(it, text) }
    }

    /** Poste le brouillon courant ; l'ajoute en fin de liste à la réussite. */
    fun submit() {
        val id = serverId ?: return
        val body = _state.value.draft.trim()
        if (body.isEmpty() || _state.value.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val created = api.post(id, CreateCommentRequest(body))
                // Envoi réussi : le brouillon persisté n'a plus lieu d'être.
                drafts.clear(id)
                _state.update {
                    it.copy(
                        submitting = false,
                        draft = "",
                        comments = it.comments + created,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        submitting = false,
                        error = e.message ?: "Échec de l'envoi.",
                    )
                }
            }
        }
    }

    /** Ouvre l'édition de [comment] en pré-remplissant son texte courant. */
    fun startEdit(comment: FlowerCommentDto) {
        _state.update { it.copy(editingId = comment.id, editDraft = comment.body, error = null) }
    }

    /** Met à jour le texte en cours d'édition. */
    fun updateEditDraft(text: String) {
        _state.update { it.copy(editDraft = text) }
    }

    /** Abandonne l'édition en cours. */
    fun cancelEdit() {
        _state.update { it.copy(editingId = null, editDraft = "") }
    }

    /** Envoie l'édition du commentaire courant et remplace la version affichée. */
    fun submitEdit() {
        val id = serverId ?: return
        val commentId = _state.value.editingId ?: return
        val body = _state.value.editDraft.trim()
        if (body.isEmpty() || _state.value.editSubmitting) return
        _state.update { it.copy(editSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                val updated = api.update(id, commentId, UpdateCommentRequest(body))
                _state.update {
                    it.copy(
                        editSubmitting = false,
                        editingId = null,
                        editDraft = "",
                        comments = it.comments.map { c -> if (c.id == updated.id) updated else c },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        editSubmitting = false,
                        error = e.message ?: "Échec de la modification.",
                    )
                }
            }
        }
    }

    /** Supprime [comment] côté serveur puis de la liste affichée. */
    fun delete(comment: FlowerCommentDto) {
        val id = serverId ?: return
        if (_state.value.deletingId != null) return
        _state.update { it.copy(deletingId = comment.id, error = null) }
        viewModelScope.launch {
            val ok = runCatching { api.delete(id, comment.id) }
                .getOrNull()?.isSuccessful == true
            _state.update {
                if (ok) {
                    it.copy(
                        deletingId = null,
                        comments = it.comments.filterNot { c -> c.id == comment.id },
                    )
                } else {
                    it.copy(deletingId = null, error = "Échec de la suppression.")
                }
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    val drafts = PrefsCommentDraftStore(context.applicationContext)
                    return CommentsViewModel(apis.comments, drafts) as T
                }
            }
    }
}

/**
 * Section « Commentaires » d'une fleur : liste chronologique des messages (auteur
 * + texte + ancienneté) suivie d'un champ de saisie. Affichée aussi bien sur le
 * détail (propriétaire) que sur une fleur partagée (ami).
 */
@Composable
fun CommentsSection(
    viewModel: CommentsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (state.comments.isEmpty()) {
                "Commentaires"
            } else {
                "Commentaires (${state.comments.size})"
            },
            style = MaterialTheme.typography.titleMedium,
        )

        if (state.comments.isEmpty() && !state.loading) {
            Text(
                text = "Aucun commentaire. Lancez la discussion !",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.comments.forEach { comment ->
            CommentCard(
                comment = comment,
                deleting = state.deletingId == comment.id,
                editing = state.editingId == comment.id,
                editDraft = state.editDraft,
                editSubmitting = state.editSubmitting,
                onDelete = { viewModel.delete(comment) },
                onStartEdit = { viewModel.startEdit(comment) },
                onEditDraftChange = viewModel::updateEditDraft,
                onSubmitEdit = { viewModel.submitEdit() },
                onCancelEdit = { viewModel.cancelEdit() },
            )
        }

        OutlinedTextField(
            value = state.draft,
            onValueChange = viewModel::updateDraft,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ajouter un commentaire…") },
            enabled = !state.submitting,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { viewModel.submit() }),
            trailingIcon = {
                TextButton(
                    onClick = { viewModel.submit() },
                    enabled = state.draft.isNotBlank() && !state.submitting,
                ) {
                    Text("Envoyer")
                }
            },
        )

        state.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Fil de discussion d'une fleur présenté en bottom sheet, réutilisé partout où
 * l'on commente une fleur distante (feed « Partagées avec moi », écran « à
 * identifier »…). La clé [flowerServerId] isole un ViewModel par fleur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    flowerServerId: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val commentsVm: CommentsViewModel = viewModel(
        key = "comments-$flowerServerId",
        factory = CommentsViewModel.factory(LocalContext.current),
    )
    LaunchedEffect(flowerServerId) {
        commentsVm.bind(flowerServerId)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        CommentsSection(
            viewModel = commentsVm,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
        )
    }
}

/**
 * Affiché à la place du fil quand la fleur n'est pas encore synchronisée : les
 * commentaires vivent côté serveur, on invite donc l'utilisateur à synchroniser
 * plutôt que de masquer silencieusement la section.
 */
@Composable
fun CommentsLockedNotice(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Commentaires",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Synchronisez cette fleur pour lancer la discussion : " +
                "connectez-vous et activez la synchronisation dans les réglages.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CommentCard(
    comment: FlowerCommentDto,
    deleting: Boolean,
    editing: Boolean,
    editDraft: String,
    editSubmitting: Boolean,
    onDelete: () -> Unit,
    onStartEdit: () -> Unit,
    onEditDraftChange: (String) -> Unit,
    onSubmitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = comment.authorName.ifBlank { "Quelqu'un" },
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // Suffixe « · modifié » quand l'auteur a édité son commentaire.
                        text = relativeTime(comment.createdAt) +
                            if (comment.editedAt != null) " · modifié" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if ((comment.canEdit || comment.canDelete) && !editing) {
                        CommentActionsMenu(
                            canEdit = comment.canEdit,
                            canDelete = comment.canDelete,
                            deleting = deleting,
                            onEdit = onStartEdit,
                            onDelete = onDelete,
                        )
                    }
                }
            }
            if (editing) {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = onEditDraftChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editSubmitting,
                    maxLines = 4,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancelEdit, enabled = !editSubmitting) {
                        Text("Annuler")
                    }
                    TextButton(
                        onClick = onSubmitEdit,
                        enabled = editDraft.isNotBlank() && !editSubmitting,
                    ) {
                        Text("Enregistrer")
                    }
                }
            } else {
                Text(
                    text = comment.body,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Menu contextuel d'un commentaire : « Éditer » (auteur) et « Supprimer ». */
@Composable
private fun CommentActionsMenu(
    canEdit: Boolean,
    canDelete: Boolean,
    deleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, enabled = !deleting) {
        Text(text = "⋮", style = MaterialTheme.typography.bodyMedium)
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (canEdit) {
            DropdownMenuItem(
                text = { Text("Éditer") },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
        }
        if (canDelete) {
            DropdownMenuItem(
                text = { Text("Supprimer") },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Ancienneté lisible d'un horodatage ISO-8601 (« à l'instant », « il y a 3 h »). */
private fun relativeTime(iso: String): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 60 * 24 -> "il y a ${minutes / 60} h"
        else -> "il y a ${minutes / (60 * 24)} j"
    }
}
