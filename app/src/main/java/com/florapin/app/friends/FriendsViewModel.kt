package com.florapin.app.friends

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.CreateFriendshipRequest
import com.florapin.app.network.dto.FriendshipDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
class FriendsViewModel(private val api: FriendshipsApi) : ViewModel() {

    private val _state = MutableStateFlow(FriendsUiState(loading = true))
    val state: StateFlow<FriendsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                _state.value = categorize(api.list())
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = messageOf(e)) }
            }
        }
    }

    /** Envoie une demande d'ami à un utilisateur (par son identifiant). */
    fun invite(userId: String) {
        val id = userId.trim()
        if (id.isEmpty()) return
        viewModelScope.launch {
            try {
                api.request(CreateFriendshipRequest(id))
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

    private fun messageOf(e: Exception): String =
        e.message ?: "Erreur réseau. Réessayez."

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
                    return FriendsViewModel(apis.friendships) as T
                }
            }
    }
}
