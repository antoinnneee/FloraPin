package com.florapin.app.friends

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.FriendProfileDto
import com.florapin.app.ui.components.networkErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'écran « profil d'ami » (TÂCHE 5.7) : chargement / profil / erreur. */
data class FriendProfileUiState(
    val loading: Boolean = true,
    val profile: FriendProfileDto? = null,
    val error: String? = null,
)

/**
 * Charge le profil public limité d'un ami (TÂCHE 5.7) via `GET /users/:id/profile`.
 * Le serveur borne déjà la réponse à ce qui m'est accessible (fleurs partagées /
 * diffusées), on se contente donc d'afficher.
 */
class FriendProfileViewModel(
    private val userId: String,
    private val api: FriendshipsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(FriendProfileUiState())
    val state: StateFlow<FriendProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val profile = api.profile(userId)
                _state.value = FriendProfileUiState(loading = false, profile = profile)
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = networkErrorMessage(e))
                }
            }
        }
    }

    companion object {
        fun factory(context: Context, userId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return FriendProfileViewModel(userId, apis.friendships) as T
                }
            }
    }
}
