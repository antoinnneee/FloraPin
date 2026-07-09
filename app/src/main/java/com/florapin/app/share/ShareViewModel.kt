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
import com.florapin.app.network.dto.ShareToAllFriendsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Périmètre des partages créés depuis la feuille : la fleur affichée, rien d'autre. */
private const val SCOPE_FLOWER = "flower"

/**
 * État de la feuille de partage : amis disponibles (les destinataires récents
 * d'abord), partages existants de la fleur.
 */
data class ShareUiState(
    val loading: Boolean = false,
    val friends: List<FriendUserDto> = emptyList(),
    val shares: List<ShareDto> = emptyList(),
    val error: String? = null,
)

/**
 * Logique de partage d'une fleur : liste les amis (acceptés) et les partages
 * existants, crée un partage (destinataire + GPS), révoque. APIs injectées (test).
 *
 * Création et révocation mettent l'état à jour sur place plutôt que de relancer
 * un [load] complet : recréer l'état viderait un instant la liste des partages,
 * ce qui ramènerait la feuille en haut et forcerait l'utilisateur à re-scroller.
 */
class ShareViewModel(
    private val friendshipsApi: FriendshipsApi,
    private val sharesApi: SharesApi,
    private val recents: RecentRecipientsStore,
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
                _state.value = ShareUiState(
                    loading = false,
                    friends = sortByRecency(friends),
                    shares = shares,
                )
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = messageOf(e)) }
            }
        }
    }

    /**
     * Amis triés par usage : ceux récemment choisis comme destinataires en tête
     * (dans l'ordre du plus récent), le reste ensuite dans l'ordre du serveur.
     * La feuille n'affiche en raccourci que les premiers.
     */
    private fun sortByRecency(friends: List<FriendUserDto>): List<FriendUserDto> {
        val rank = recents.recentFriendIds().withIndex().associate { (i, id) -> id to i }
        val (recent, others) = friends.partition { it.id in rank }
        return recent.sortedBy { rank.getValue(it.id) } + others
    }

    /** Partage la fleur [flowerId] avec un ami précis. */
    fun createShare(friendId: String, includeGps: Boolean, flowerId: String) {
        viewModelScope.launch {
            try {
                val share = sharesApi.create(
                    CreateShareRequest(
                        friendId = friendId,
                        scope = SCOPE_FLOWER,
                        flowerId = flowerId,
                        albumId = null,
                        includeGps = includeGps,
                    ),
                )
                recents.rememberRecentFriend(friendId)
                _state.update { it.copy(shares = it.shares + share, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = messageOf(e)) }
            }
        }
    }

    /**
     * Partage la fleur avec *tous* les amis acceptés en une requête
     * ([SharesApi.createForAllFriends]) : un partage réseau unique, qui vaudra
     * aussi pour les amis ajoutés plus tard.
     */
    fun createShareForAll(includeGps: Boolean, flowerId: String) {
        viewModelScope.launch {
            try {
                val share = sharesApi.createForAllFriends(
                    ShareToAllFriendsRequest(
                        scope = SCOPE_FLOWER,
                        flowerId = flowerId,
                        albumId = null,
                        includeGps = includeGps,
                    ),
                )
                _state.update { it.copy(shares = it.shares + share, error = null) }
            } catch (e: Exception) {
                _state.update { it.copy(error = messageOf(e)) }
            }
        }
    }

    /**
     * Révoque un partage. La ligne disparaît immédiatement ; si l'appel échoue,
     * elle est remise à sa place et l'erreur est affichée.
     */
    fun revoke(id: String) {
        val previous = _state.value.shares
        if (previous.none { it.id == id }) return
        _state.update { it.copy(shares = it.shares.filterNot { s -> s.id == id }, error = null) }
        viewModelScope.launch {
            try {
                sharesApi.revoke(id)
            } catch (e: Exception) {
                _state.update { it.copy(shares = previous, error = messageOf(e)) }
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
                    val appContext = context.applicationContext
                    val tokenStore = EncryptedTokenStore(appContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return ShareViewModel(
                        apis.friendships,
                        apis.shares,
                        SharePreferences(appContext),
                    ) as T
                }
            }
    }
}
