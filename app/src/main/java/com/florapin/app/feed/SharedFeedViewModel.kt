package com.florapin.app.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.FlowerDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Une fleur partagée + le nom de l'ami qui la partage (si connu). */
data class SharedFlowerItem(
    val flower: FlowerDto,
    val ownerName: String?,
)

data class SharedFeedUiState(
    val loading: Boolean = false,
    val items: List<SharedFlowerItem> = emptyList(),
    val error: String? = null,
)

/**
 * Feed des fleurs partagées avec l'utilisateur (SharesApi.sharedWithMe), enrichi
 * du nom de l'ami propriétaire (résolu via la liste d'amitiés).
 */
class SharedFeedViewModel(
    private val sharesApi: SharesApi,
    private val friendshipsApi: FriendshipsApi,
) : ViewModel() {

    private val _state = MutableStateFlow(SharedFeedUiState(loading = true))
    val state: StateFlow<SharedFeedUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val flowers = sharesApi.sharedWithMe()
                val names = friendshipsApi.list().associate { it.user.id to it.user.displayName }
                _state.value = SharedFeedUiState(
                    loading = false,
                    items = flowers.map { SharedFlowerItem(it, names[it.ownerId]) },
                )
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Erreur réseau. Réessayez.")
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
                    return SharedFeedViewModel(apis.shares, apis.friendships) as T
                }
            }
    }
}
