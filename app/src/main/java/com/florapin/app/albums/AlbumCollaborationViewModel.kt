package com.florapin.app.albums

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.florapin.app.data.AlbumEntity
import com.florapin.app.data.AlbumRepository
import com.florapin.app.data.applyTo
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.AlbumPermissionDto
import com.florapin.app.network.dto.CreateGroupRequest
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.network.dto.GroupDto
import com.florapin.app.network.dto.InviteMemberRequest
import com.florapin.app.network.dto.SetAlbumGroupRequest
import com.florapin.app.network.dto.SetAlbumPermissionsRequest
import com.florapin.app.ui.components.networkErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de collaboration d'un album (TÂCHE 7.1). */
data class CollaborationUiState(
    val loading: Boolean = false,
    val error: String? = null,
    /** Groupe de l'album (null tant que l'album est solo). */
    val group: GroupDto? = null,
    /** Amis acceptés non encore membres, proposables à l'invitation. */
    val invitableFriends: List<FriendshipDto> = emptyList(),
    /** Régime de droits courant : 'open' | 'restricted'. */
    val permissionMode: String = "open",
    /** Droits « au cas par cas » (mode restreint), par userId. */
    val permissions: Map<String, Boolean> = emptyMap(),
    val isOwner: Boolean = false,
)

/**
 * Gère la collaboration d'un album (TÂCHE 7.1) : rendre collaboratif, membres,
 * invitations, régime de droits. Purement réseau — device-first : hors-ligne,
 * l'écran affiche une erreur et reste en lecture seule.
 */
class AlbumCollaborationViewModel(application: Application) :
    AndroidViewModel(application) {

    private val repository = AlbumRepository.from(application)
    private val tokenStore = EncryptedTokenStore(application.applicationContext)
    private val apis by lazy { NetworkModule.createAuthenticated(tokenStore) }
    private val selfUserId: String? get() = tokenStore.userId()

    private val _state = MutableStateFlow(CollaborationUiState())
    val state: StateFlow<CollaborationUiState> = _state.asStateFlow()

    private var album: AlbumEntity? = null

    /** Charge l'état de collaboration pour l'album courant. */
    fun load(album: AlbumEntity) {
        this.album = album
        val groupId = album.groupId
        if (groupId == null) {
            _state.value = CollaborationUiState(
                permissionMode = album.permissionMode,
            )
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val group = apis.groups.get(groupId)
                val friends = apis.friendships.list()
                    .filter { it.status == "accepted" }
                val memberIds = group.members.map { it.userId }.toSet()
                // L'album serveur porte le régime de droits ET les droits par membre.
                val serverId = album.serverId
                val albumDto = serverId?.let { runCatching { apis.albums.get(it) }.getOrNull() }
                _state.value = CollaborationUiState(
                    group = group,
                    invitableFriends = friends.filter { it.user.id !in memberIds },
                    permissionMode = albumDto?.permissionMode ?: album.permissionMode,
                    permissions = albumDto?.permissions
                        ?.associate { it.userId to it.canEdit }
                        ?: emptyMap(),
                    isOwner = group.ownerId == selfUserId,
                )
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = networkErrorMessage(e)) }
            }
        }
    }

    /**
     * Rend un album solo collaboratif : crée un groupe et rattache l'album.
     * Réservé au propriétaire d'un album déjà synchronisé (serverId requis).
     */
    fun makeCollaborative() {
        val current = album ?: return
        val serverId = current.serverId ?: run {
            _state.update {
                it.copy(error = "Synchronisez l'album avant de le partager en groupe.")
            }
            return
        }
        viewModelScope.launch {
            try {
                val group = apis.groups.create(CreateGroupRequest(name = current.name))
                val dto = apis.albums.setGroup(
                    serverId,
                    SetAlbumGroupRequest(groupId = group.id, permissionMode = "open"),
                )
                persistCollaboration(dto, current)
                load(album!!)
            } catch (e: Exception) {
                _state.update { it.copy(error = networkErrorMessage(e)) }
            }
        }
    }

    fun invite(friendUserId: String) = act { groupId ->
        apis.groups.invite(groupId, InviteMemberRequest(friendUserId))
    }

    fun removeMember(userId: String) = act { groupId ->
        apis.groups.removeMember(groupId, userId)
    }

    /** Bascule le régime de droits (owner uniquement). */
    fun setPermissionMode(mode: String) {
        val current = album ?: return
        val serverId = current.serverId ?: return
        viewModelScope.launch {
            try {
                val entries = _state.value.permissions.map {
                    AlbumPermissionDto(userId = it.key, canEdit = it.value)
                }
                val dto = apis.albums.setPermissions(
                    serverId,
                    SetAlbumPermissionsRequest(mode = mode, entries = entries),
                )
                persistCollaboration(dto, current)
                load(album!!)
            } catch (e: Exception) {
                _state.update { it.copy(error = networkErrorMessage(e)) }
            }
        }
    }

    /** Accorde/retire le droit d'édition d'un membre (mode restreint). */
    fun setMemberCanEdit(userId: String, canEdit: Boolean) {
        val current = album ?: return
        val serverId = current.serverId ?: return
        viewModelScope.launch {
            try {
                val merged = _state.value.permissions.toMutableMap()
                merged[userId] = canEdit
                val entries = merged.map {
                    AlbumPermissionDto(userId = it.key, canEdit = it.value)
                }
                val dto = apis.albums.setPermissions(
                    serverId,
                    SetAlbumPermissionsRequest(mode = "restricted", entries = entries),
                )
                persistCollaboration(dto, current)
                load(album!!)
            } catch (e: Exception) {
                _state.update { it.copy(error = networkErrorMessage(e)) }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun act(action: suspend (groupId: String) -> Unit) {
        val groupId = album?.groupId ?: return
        viewModelScope.launch {
            try {
                action(groupId)
                load(album!!)
            } catch (e: Exception) {
                _state.update { it.copy(error = networkErrorMessage(e)) }
            }
        }
    }

    private suspend fun persist(updated: AlbumEntity) {
        album = updated
        repository.update(updated)
    }

    /**
     * Une réponse de réglage ne doit pas effacer un titre ou une couverture
     * encore en attente de synchronisation locale.
     */
    private suspend fun persistCollaboration(
        dto: com.florapin.app.network.dto.AlbumDto,
        current: AlbumEntity,
    ) {
        persist(
            dto.applyTo(current).copy(
                name = current.name,
                coverFlowerId = current.coverFlowerId,
                updatedAt = current.updatedAt,
                syncState = current.syncState,
                deletedAt = current.deletedAt,
            ),
        )
    }
}
