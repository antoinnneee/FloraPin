package com.florapin.app.share

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.AlbumsApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.SharesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.AlbumDto
import com.florapin.app.network.dto.CreateShareRequest
import com.florapin.app.network.dto.FriendUserDto
import com.florapin.app.network.dto.ShareDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** État de la feuille de partage : amis disponibles, albums, partages existants. */
data class ShareUiState(
    val loading: Boolean = false,
    val friends: List<FriendUserDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
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
    private val albumsApi: AlbumsApi,
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
                val albums = albumsApi.list()
                val shares = sharesApi.listMine()
                _state.value = ShareUiState(
                    loading = false,
                    friends = friends,
                    albums = albums,
                    shares = shares,
                )
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = messageOf(e)) }
            }
        }
    }

    /**
     * Crée un partage. [flowerId] est requis pour le périmètre 'flower',
     * [albumId] pour 'album' ; tous deux ignorés (null) pour 'all'.
     */
    fun createShare(
        friendId: String,
        scope: String,
        includeGps: Boolean,
        flowerId: String?,
        albumId: String? = null,
    ) {
        viewModelScope.launch {
            try {
                sharesApi.create(
                    CreateShareRequest(
                        friendId = friendId,
                        scope = scope,
                        flowerId = if (scope == "flower") flowerId else null,
                        albumId = if (scope == "album") albumId else null,
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

    /**
     * Message d'erreur lisible. Pour une réponse HTTP, on extrait le champ
     * `message` du corps JSON renvoyé par le backend (ex. « Le partage est
     * réservé aux amis acceptés. ») plutôt que d'afficher « HTTP 4xx ».
     */
    private fun messageOf(e: Exception): String = when (e) {
        is HttpException -> apiMessage(e) ?: "Erreur réseau. Réessayez."
        else -> e.message ?: "Erreur réseau. Réessayez."
    }

    private fun apiMessage(e: HttpException): String? =
        runCatching {
            val body = e.response()?.errorBody()?.string() ?: return null
            MESSAGE_REGEX.find(body)?.groupValues?.get(1)
        }.getOrNull()

    companion object {
        /** Extrait la valeur du champ JSON `message` d'un corps d'erreur NestJS. */
        private val MESSAGE_REGEX = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"")

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return ShareViewModel(apis.friendships, apis.shares, apis.albums) as T
                }
            }
    }
}
