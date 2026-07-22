package com.florapin.app.friends

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.AddFriendByIdRequest
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.FriendshipDto
import com.florapin.app.ui.components.networkErrorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** État de l'écran amis : listes catégorisées + chargement/erreur. */
data class FriendsUiState(
    val loading: Boolean = false,
    val incoming: List<FriendshipDto> = emptyList(),
    val outgoing: List<FriendshipDto> = emptyList(),
    val friends: List<FriendshipDto> = emptyList(),
    val error: String? = null,
)

/**
 * Gestion des amitiés : liste (amis acceptés, demandes entrantes/sortantes),
 * invitation par identifiant utilisateur, acceptation, suppression.
 */
class FriendsViewModel(
    private val api: FriendshipsApi,
    /** Suivi des demandes entrantes vues (badge). Null en test : remise à 0 ignorée. */
    private val badgeStore: FriendsBadgeStore? = null,
    /** Id de l'utilisateur courant (encodé dans son QR — TÂCHE 4.5). Null si inconnu. */
    val selfUserId: String? = null,
    /** Nom affiché de l'utilisateur courant (légende du QR). */
    val selfDisplayName: String = "",
) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState(loading = true))
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            FriendshipEvents.changes.collectLatest {
                refresh(showLoading = false)
            }
        }
    }

    fun refresh(showLoading: Boolean = true) {
        if (showLoading) {
            _state.update { it.copy(loading = true, error = null) }
        }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val friendships = enrichExistingAvatars(api.list())
                val categorized = categorize(friendships)
                _state.value = categorized
                // Ouvrir l'écran « voit » les demandes entrantes : le badge revient
                // à 0, même sans avoir accepté/refusé.
                badgeStore?.markSeen(categorized.incoming.map { it.id }.toSet())
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = if (showLoading) messageOf(e) else it.error,
                    )
                }
            }
        }
    }

    /**
     * Compatibilité avec les serveurs qui ne renvoient pas encore `avatarUrl`
     * dans la liste des amitiés. Le profil public permet de conserver les
     * photos choisies avant l'ajout des avatars illustrés.
     */
    private suspend fun enrichExistingAvatars(
        friendships: List<FriendshipDto>,
    ): List<FriendshipDto> = supervisorScope {
        friendships.map { friendship ->
            async {
                if (friendship.status != "accepted" ||
                    !friendship.user.avatarUrl.isNullOrBlank()
                ) {
                    return@async friendship
                }

                val avatarUrl = try {
                    api.profile(friendship.user.id).avatarUrl
                } catch (_: Exception) {
                    null
                }
                if (avatarUrl.isNullOrBlank()) {
                    friendship
                } else {
                    friendship.copy(user = friendship.user.copy(avatarUrl = avatarUrl))
                }
            }
        }.awaitAll()
    }

    /** Envoie une demande d'ami à un utilisateur (par son email). */
    fun invite(email: String) {
        val cleaned = email.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            try {
                api.request(CreateFriendshipRequest(cleaned))
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = messageOf(e)) }
            }
        }
    }

    /**
     * Ajoute un ami à partir d'un QR code scanné (TÂCHE 4.5). Décode le contenu
     * (extrait l'UUID) : un QR étranger ou son propre code est refusé sans appel
     * réseau. L'acceptation croisée éventuelle est gérée côté serveur.
     */
    fun addByScan(payload: String) {
        val userId = FriendQrCodec.decode(payload)
        if (userId == null) {
            _state.update { it.copy(error = "QR code non reconnu.") }
            return
        }
        if (userId == selfUserId) {
            _state.update {
                it.copy(error = "C'est votre propre code : demandez à un ami de le scanner.")
            }
            return
        }
        viewModelScope.launch {
            try {
                api.requestById(AddFriendByIdRequest(userId))
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = messageOf(e)) }
            }
        }
    }

    fun accept(id: String) = act { api.accept(id) }

    fun remove(id: String) = act { api.remove(id) }

    private fun act(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = messageOf(e)) }
            }
        }
    }

    // Mapping réseau commun (TÂCHE 6.16) : hors-ligne / serveur injoignable / 4xx.
    private fun messageOf(e: Exception): String = networkErrorMessage(e)

    companion object {
        /** Répartit les relations en demandes entrantes/sortantes et amis. */
        fun categorize(all: List<FriendshipDto>): FriendsUiState = FriendsUiState(
            loading = false,
            incoming = all.filter { it.status == "pending" && it.direction == "incoming" },
            outgoing = all.filter { it.status == "pending" && it.direction == "outgoing" },
            friends = all.filter { it.status == "accepted" },
        )

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return FriendsViewModel(
                        apis.friendships,
                        FriendsBadgeStore(context.applicationContext),
                        selfUserId = tokenStore.userId(),
                        selfDisplayName = tokenStore.displayName().orEmpty(),
                    ) as T
                }
            }
    }
}
