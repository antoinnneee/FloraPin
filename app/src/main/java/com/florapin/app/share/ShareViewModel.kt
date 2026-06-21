package com.florapin.app.share

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.CreateShareRequest
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.ShareDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de la feuille de partage : amis disponibles, partages existants. */
data class ShareUiState(
    val loading: Boolean = false,
    val friends: List<FriendUserDto> = emptyList(),
    val shares: List<ShareDto> = emptyList(),
    val error: String? = null,
)

/**
 * Logique de partage d'une fleur : liste les amis (acceptés) et les partages
 * existants, crée un partage (périmètre + GPS), révoque. APIs injectées (test).
 */
class ShareViewModel(
    private val friendshipsApi: FriendshipsApi,
    private val sharesApi: SharesApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ShareUiState(loading = true))
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val friends = friendshipsApi.list()
                    .filter { it.status == "accepted" }
                    .map { it.user }
                val shares = sharesApi.listMine()
                _state.value = ShareUiState(loading = false, friends = friends, shares = shares)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = messageOf(e)) }
            }
        }
    }

    /**
     * Crée un partage. [flowerId] est requis pour le périmètre 'flower' ; ignoré
     * (null) pour 'all'.
     */
    fun createShare(
        friendId: String,
        scope: String,
        includeGps: Boolean,
        flowerId: String?,
    ) {
        viewModelScope.launch {
            try {
                sharesApi.create(
                    CreateShareRequest(
                        friendId = friendId,
                        scope = scope,
                        flowerId = if (scope == "flower") flowerId else null,
                        includeGps = includeGps,
                    ),
                )
                load()
            } catch (e: Exception) {
                _state.update { it.copy(error = messageOf(e)) }
            }
        }
    }

    fun revoke(id: String) {
        viewModelScope.launch {
            try {
                sharesApi.revoke(id)
                load()
            } catch (e: Exception) {
                _state.update { it.copy(error = messageOf(e)) }
            }
        }
    }

    private fun messageOf(e: Exception): String =
        e.message ?: "Erreur réseau. Réessayez."

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return ShareViewModel(apis.friendships, apis.shares) as T
                }
            }
    }
}
