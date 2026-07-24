package com.florapin.app.detail

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.R
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.CommentsApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.CreateCommentRequest
import com.florapin.app.network.dto.FlowerCommentDto
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.UpdateCommentRequest
import com.florapin.app.ui.components.BotanicalIcon
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
    /** Commentaire auquel le brouillon répond (citation), `null` sinon. */
    val replyingTo: FlowerCommentDto? = null,
    /** Amis acceptés (mentionnables), chargés une fois pour l'autocomplete `@`. */
    val friends: List<FriendUserDto> = emptyList(),
    /** Table `id → nom` des amis, pour rendre `@[id]` en `@Nom` dans la saisie. */
    val friendNamesById: Map<String, String> = emptyMap(),
    /** Suggestions courantes de l'autocomplete `@` (vide si pas de saisie `@…`). */
    val mentionSuggestions: List<FriendUserDto> = emptyList(),
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
    private val friendships: FriendshipsApi? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    private var serverId: String? = null

    /** Associe la fleur [flowerServerId] (idempotent) et charge ses commentaires. */
    fun bind(flowerServerId: String) {
        // Amis mentionnables : indépendants de la fleur, chargés une seule fois.
        loadFriends()
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

    /**
     * Charge les amis acceptés (mentionnables) une seule fois. Best-effort et
     * device-first : sans réseau / sync, l'autocomplete reste simplement vide, la
     * saisie de commentaires n'est pas bloquée.
     */
    private fun loadFriends() {
        val friendshipsApi = friendships ?: return
        if (_state.value.friends.isNotEmpty()) return
        viewModelScope.launch {
            val accepted = runCatching { friendshipsApi.list() }.getOrNull()
                ?.filter { it.status == "accepted" }
                ?.map { it.user }
                ?: return@launch
            _state.update {
                it.copy(
                    friends = accepted,
                    friendNamesById = accepted.associate { u -> u.id to u.displayName },
                )
            }
        }
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

    /**
     * Met à jour le brouillon du champ de saisie (et le persiste par fleur), puis
     * recalcule les suggestions de mention selon la requête `@…` en cours.
     */
    fun updateDraft(text: String) {
        _state.update { it.copy(draft = text, mentionSuggestions = suggestionsFor(text)) }
        serverId?.let { drafts.save(it, text) }
    }

    /**
     * Insère la mention encodée de [friend] à la place de la requête `@…` en cours,
     * ferme les suggestions et renvoie le nouveau texte (pour repositionner le
     * curseur côté UI). Le brouillon encode l'id (`@[userId]`), pas le nom.
     */
    fun selectMention(friend: FriendUserDto): String {
        val newText = MentionText.insertMention(_state.value.draft, friend.id)
        _state.update {
            it.copy(
                draft = newText,
                // L'ami vient d'être choisi : garantit que son nom est résolvable
                // même si la liste d'amis a évolué entre-temps.
                friendNamesById = it.friendNamesById + (friend.id to friend.displayName),
                mentionSuggestions = emptyList(),
            )
        }
        serverId?.let { drafts.save(it, newText) }
        return newText
    }

    /** Amis dont le nom contient la requête `@…` courante (max 6), sinon vide. */
    private fun suggestionsFor(text: String): List<FriendUserDto> {
        val query = MentionText.activeQuery(text) ?: return emptyList()
        return _state.value.friends
            .filter { it.displayName.contains(query, ignoreCase = true) }
            .take(6)
    }

    /** Cible [comment] pour une réponse citée (le prochain envoi lui répondra). */
    fun startReply(comment: FlowerCommentDto) {
        _state.update { it.copy(replyingTo = comment, error = null) }
    }

    /** Abandonne la réponse citée en cours : le prochain envoi repart au fil. */
    fun cancelReply() {
        _state.update { it.copy(replyingTo = null) }
    }

    /** Poste le brouillon courant ; l'ajoute en fin de liste à la réussite. */
    fun submit() {
        val id = serverId ?: return
        val body = _state.value.draft.trim()
        if (body.isEmpty() || _state.value.submitting) return
        val replyToId = _state.value.replyingTo?.id
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                val created = api.post(id, CreateCommentRequest(body, replyToId))
                // Envoi réussi : le brouillon persisté n'a plus lieu d'être.
                drafts.clear(id)
                _state.update {
                    it.copy(
                        submitting = false,
                        draft = "",
                        replyingTo = null,
                        mentionSuggestions = emptyList(),
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
                    return CommentsViewModel(apis.comments, drafts, apis.friendships) as T
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
    scrollComments: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (state.comments.isEmpty()) {
                "Commentaires"
            } else {
                "Commentaires (${state.comments.size})"
            },
            style = MaterialTheme.typography.titleMedium,
        )

        val commentsModifier = if (scrollComments) {
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        CommentList(
            state = state,
            viewModel = viewModel,
            modifier = commentsModifier,
        )

        state.replyingTo?.let { target ->
            ReplyBanner(target = target, onCancel = { viewModel.cancelReply() })
        }

        // Champ de saisie avec autocomplete de mention @ami. On tient une valeur
        // locale TextFieldValue pour maîtriser le curseur (placé en fin après
        // insertion d'une mention), tout en gardant le brouillon persisté (String)
        // dans le ViewModel.
        val mentionColor = MaterialTheme.colorScheme.primary
        var field by remember {
            mutableStateOf(TextFieldValue(state.draft, TextRange(state.draft.length)))
        }
        LaunchedEffect(state.draft) {
            // Resynchronise sur un changement venu du ViewModel (restauration à
            // l'ouverture, effacement après envoi, insertion d'une mention).
            if (field.text != state.draft) {
                field = TextFieldValue(state.draft, TextRange(state.draft.length))
            }
        }

        // Suggestions d'amis à mentionner, présentées au-dessus du champ.
        state.mentionSuggestions.forEach { friend ->
            MentionSuggestionRow(
                name = friend.displayName.ifBlank { "Sans nom" },
                onClick = {
                    val newText = viewModel.selectMention(friend)
                    field = TextFieldValue(newText, TextRange(newText.length))
                },
            )
        }

        OutlinedTextField(
            value = field,
            onValueChange = { newValue ->
                field = newValue
                viewModel.updateDraft(newValue.text)
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
            placeholder = {
                Text(
                    if (state.replyingTo != null) "Écrire une réponse…"
                    else "Ajouter un commentaire… (@ pour mentionner)",
                )
            },
            enabled = !state.submitting,
            maxLines = 4,
            visualTransformation = remember(state.friendNamesById, mentionColor) {
                MentionVisualTransformation(state.friendNamesById, mentionColor)
            },
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
            scrollComments = true,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
        )
    }
}

/**
 * Liste des commentaires. Dans la bottom sheet, elle prend uniquement l'espace
 * disponible et défile ; le champ de saisie conserve ainsi sa hauteur minimale.
 */
@Composable
private fun CommentList(
    state: CommentsUiState,
    viewModel: CommentsViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
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
                onReply = { viewModel.startReply(comment) },
            )
        }
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
    onReply: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Citation : rappel de l'auteur et du texte du commentaire répondu.
            if (comment.replyToId != null) {
                CommentQuote(
                    authorName = comment.replyToAuthorName,
                    body = comment.replyToBody,
                )
            }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        // Rend les mentions `@[id]` en `@Nom` colorées (noms résolus
                        // par l'API à la lecture — un renommage reste cohérent).
                        text = commentBodyAnnotated(comment),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onReply,
                        modifier = Modifier.height(40.dp),
                    ) {
                        Text("Répondre")
                    }
                }
            }
        }
    }
}

/**
 * Citation d'un commentaire parent, affichée en tête d'une réponse : nom de
 * l'auteur cité et rappel tronqué de son texte, dans un liseré discret.
 */
@Composable
private fun CommentQuote(authorName: String?, body: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "↩ ${authorName?.ifBlank { "Quelqu'un" } ?: "Quelqu'un"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!body.isNullOrBlank()) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Bandeau « En réponse à … » affiché au-dessus du champ de saisie quand une
 * réponse citée est en cours de rédaction, avec un bouton d'annulation.
 */
@Composable
private fun ReplyBanner(target: FlowerCommentDto, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "En réponse à ${target.authorName.ifBlank { "Quelqu'un" }}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onCancel) {
            Text("Annuler")
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
    IconButton(
        onClick = { expanded = true },
        enabled = !deleting,
        modifier = Modifier.size(40.dp),
    ) {
        BotanicalIcon(
            iconRes = R.drawable.ic_more_botanical,
            contentDescription = "Actions du commentaire",
            size = 22.dp,
        )
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

/** Ligne cliquable de suggestion d'un ami à mentionner (autocomplete `@`). */
@Composable
private fun MentionSuggestionRow(name: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "@$name",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Construit le corps d'un commentaire en `AnnotatedString`, chaque mention
 * `@[userId]` rendue en `@Nom` coloré. Les noms proviennent de `comment.mentions`
 * (résolus par l'API À LA LECTURE) : un renommage reste donc cohérent, et un id
 * inconnu (ami retiré) retombe sur « @quelqu'un ».
 */
@Composable
private fun commentBodyAnnotated(comment: FlowerCommentDto): AnnotatedString {
    val mentionColor = MaterialTheme.colorScheme.primary
    return remember(comment.body, comment.mentions, mentionColor) {
        val nameById = comment.mentions.associate { it.userId to it.displayName }
        buildAnnotatedString {
            for (segment in MentionText.segments(comment.body, nameById)) {
                when (segment) {
                    is MentionText.Segment.Literal -> append(segment.text)
                    is MentionText.Segment.Mention ->
                        withStyle(
                            SpanStyle(color = mentionColor, fontWeight = FontWeight.Medium),
                        ) {
                            append(segment.display)
                        }
                }
            }
        }
    }
}

/**
 * Transformation visuelle du champ de saisie : affiche chaque mention encodée
 * `@[userId]` en `@Nom` (coloré) tout en conservant le texte BRUT comme valeur
 * réelle. Les mentions sont « atomiques » : le curseur ne se pose qu'à leurs
 * bornes (une position intérieure est ramenée à la fin du token).
 */
private class MentionVisualTransformation(
    private val nameById: Map<String, String>,
    private val mentionColor: Color,
) : VisualTransformation {

    private data class Seg(
        val rawStart: Int,
        val rawEnd: Int,
        val transStart: Int,
        val transEnd: Int,
        val isMention: Boolean,
    )

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val segs = mutableListOf<Seg>()
        val annotated = buildAnnotatedString {
            var index = 0
            for (match in MentionText.MENTION_REGEX.findAll(raw)) {
                if (match.range.first > index) {
                    val start = length
                    append(raw.substring(index, match.range.first))
                    segs.add(Seg(index, match.range.first, start, length, false))
                }
                val name = nameById[match.groupValues[1]].orEmpty().ifBlank { "quelqu'un" }
                val start = length
                withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Medium)) {
                    append("@$name")
                }
                segs.add(Seg(match.range.first, match.range.last + 1, start, length, true))
                index = match.range.last + 1
            }
            if (index < raw.length) {
                val start = length
                append(raw.substring(index))
                segs.add(Seg(index, raw.length, start, length, false))
            }
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                for (seg in segs) {
                    if (offset in seg.rawStart..seg.rawEnd) {
                        return if (seg.isMention) {
                            if (offset <= seg.rawStart) seg.transStart else seg.transEnd
                        } else {
                            seg.transStart + (offset - seg.rawStart)
                        }
                    }
                }
                return annotated.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                for (seg in segs) {
                    if (offset in seg.transStart..seg.transEnd) {
                        return if (seg.isMention) {
                            if (offset <= seg.transStart) seg.rawStart else seg.rawEnd
                        } else {
                            seg.rawStart + (offset - seg.transStart)
                        }
                    }
                }
                return raw.length
            }
        }
        return TransformedText(annotated, mapping)
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
