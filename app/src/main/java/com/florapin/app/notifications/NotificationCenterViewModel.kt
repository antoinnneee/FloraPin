package com.florapin.app.notifications

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.GroupsApi
import com.florapin.app.network.api.NotificationsApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.NotificationDto
import com.florapin.app.sync.SyncScheduler
import com.florapin.app.ui.components.networkErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État du centre de notifications. */
data class NotificationCenterUiState(
    val loading: Boolean = false,
    val markingAllRead: Boolean = false,
    val items: List<NotificationDto> = emptyList(),
    val acceptingInvitationIds: Set<String> = emptySet(),
    val acceptedInvitationIds: Set<String> = emptySet(),
    val actionError: String? = null,
    /**
     * Le service est joignable ? `false` après un échec réseau (hors-ligne,
     * non connecté) : on affiche alors un état « indisponible » explicite plutôt
     * qu'une liste vide trompeuse (device-first, cf. CommentsLockedNotice).
     */
    val available: Boolean = true,
)

/**
 * Centre de notifications in-app (TÂCHE 2.7). Charge la liste
 * (`GET notifications`) et marque une notification lue au tap
 * (`POST notifications/{id}/read`).
 *
 * Données collaboratives vivant côté serveur : indépendantes de la sync
 * device-first (comme le feed et les commentaires), mais nécessitant le réseau.
 * Hors-ligne, on dégrade proprement via [NotificationCenterUiState.available].
 */
class NotificationCenterViewModel(
    private val api: NotificationsApi,
    private val groupsApi: GroupsApi,
    private val onAlbumAccepted: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationCenterUiState(loading = true))
    val state: StateFlow<NotificationCenterUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** (Re)charge la liste des notifications. */
    fun load() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            _state.value = try {
                NotificationCenterUiState(
                    loading = false,
                    items = api.list(),
                    available = true,
                )
            } catch (e: Exception) {
                // Hors-ligne / non connecté : écran indisponible assumé.
                NotificationCenterUiState(loading = false, available = false)
            }
        }
    }

    /**
     * Marque [notification] comme lue et met à jour la liste localement (mise à
     * jour optimiste). Best-effort : un échec réseau n'annule pas l'affichage,
     * l'idempotence côté serveur permet de réessayer au rechargement.
     */
    fun markRead(notification: NotificationDto) {
        if (notification.readAt != null) return
        // Marque localement tout de suite (retire le point « non lu »).
        _state.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.id == notification.id && it.readAt == null) {
                        it.copy(readAt = NOW_PLACEHOLDER)
                    } else {
                        it
                    }
                },
            )
        }
        viewModelScope.launch {
            runCatching { api.markRead(notification.id) }
        }
    }

    /**
     * Accepte une invitation d'album sans quitter le centre de notifications.
     * L'action est idempotente côté serveur ; après succès, une synchronisation
     * forcée récupère l'album partagé même si la sync automatique est désactivée.
     */
    fun acceptAlbumInvitation(notification: NotificationDto) {
        val groupId = notification.groupId ?: return
        if (notification.id in _state.value.acceptingInvitationIds ||
            notification.id in _state.value.acceptedInvitationIds
        ) {
            return
        }
        _state.update {
            it.copy(
                acceptingInvitationIds = it.acceptingInvitationIds + notification.id,
                actionError = null,
            )
        }
        viewModelScope.launch {
            try {
                groupsApi.accept(groupId)
                _state.update {
                    it.copy(
                        acceptingInvitationIds = it.acceptingInvitationIds - notification.id,
                        acceptedInvitationIds = it.acceptedInvitationIds + notification.id,
                    )
                }
                markRead(notification)
                onAlbumAccepted()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        acceptingInvitationIds = it.acceptingInvitationIds - notification.id,
                        actionError = networkErrorMessage(e),
                    )
                }
            }
        }
    }

    fun clearActionError() = _state.update { it.copy(actionError = null) }

    /** Supprime optimistement une notification et la restaure si le serveur échoue. */
    fun delete(notification: NotificationDto) {
        val index = _state.value.items.indexOfFirst { it.id == notification.id }
        if (index < 0) return
        _state.update { state ->
            state.copy(items = state.items.filterNot { it.id == notification.id })
        }
        viewModelScope.launch {
            runCatching {
                check(api.delete(notification.id).isSuccessful)
            }.onFailure {
                _state.update { state ->
                    if (state.items.any { it.id == notification.id }) {
                        state
                    } else {
                        val restored = state.items.toMutableList().apply {
                            add(index.coerceAtMost(size), notification)
                        }
                        state.copy(items = restored)
                    }
                }
            }
        }
    }

    /** Marque toute la liste lue et restaure les éléments si la requête échoue. */
    fun markAllRead() {
        val unreadIds = _state.value.items
            .filter { it.readAt == null }
            .mapTo(mutableSetOf()) { it.id }
        if (unreadIds.isEmpty() || _state.value.markingAllRead) return

        _state.update { state ->
            state.copy(
                markingAllRead = true,
                items = state.items.map { notification ->
                    if (notification.id in unreadIds) {
                        notification.copy(readAt = NOW_PLACEHOLDER)
                    } else {
                        notification
                    }
                },
            )
        }
        viewModelScope.launch {
            runCatching { api.markAllRead() }
                .onSuccess {
                    _state.update { state -> state.copy(markingAllRead = false) }
                }
                .onFailure {
                    _state.update { state ->
                        state.copy(
                            markingAllRead = false,
                            items = state.items.map { notification ->
                                if (notification.id in unreadIds &&
                                    notification.readAt == NOW_PLACEHOLDER
                                ) {
                                    notification.copy(readAt = null)
                                } else {
                                    notification
                                }
                            },
                        )
                    }
                }
        }
    }

    companion object {
        /**
         * Repère local « lu » posé en optimiste : la vraie valeur (horodatage
         * serveur) sera relue au prochain [load]. Seule sa non-nullité compte
         * pour masquer l'indicateur de non-lu.
         */
        private const val NOW_PLACEHOLDER = "read"

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return NotificationCenterViewModel(
                        api = apis.notifications,
                        groupsApi = apis.groups,
                        onAlbumAccepted = {
                            SyncScheduler.syncNow(context.applicationContext, force = true)
                        },
                    ) as T
                }
            }
    }
}
