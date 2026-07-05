package com.florapin.app.feed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.FeedApi
import com.florapin.app.network.api.FriendshipsApi
import com.florapin.app.network.api.LikesApi
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

/** Ordre du feed (NODE-140). */
enum class FeedSort(val apiValue: String, val label: String) {
    DATE("date", "Récentes"),
    LIKES("likes", "Meilleures photos"),
}

data class SharedFeedUiState(
    val loading: Boolean = false,
    /** Rechargement via pull-to-refresh (liste déjà visible, distinct du chargement initial). */
    val refreshing: Boolean = false,
    /** Chargement d'une page suivante (pagination), distinct du chargement initial. */
    val loadingMore: Boolean = false,
    val items: List<SharedFlowerItem> = emptyList(),
    val error: String? = null,
    val sort: FeedSort = FeedSort.DATE,
    /** Toutes les pages ont été chargées : plus rien à paginer. */
    val endReached: Boolean = false,
)

/**
 * Feed des fleurs visibles par l'utilisateur (FeedApi.getFeed : partages ciblés
 * + fleurs publiées 'friends', NODE-137), enrichi du nom de l'ami propriétaire
 * (résolu via la liste d'amitiés).
 */
class SharedFeedViewModel(
    private val feedApi: FeedApi,
    private val friendshipsApi: FriendshipsApi,
    private val likesApi: LikesApi,
    /** Suivi des fleurs vues (badge onglet). Null en test : la remise à 0 est ignorée. */
    private val badgeStore: FeedBadgeStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(SharedFeedUiState(loading = true))
    val state: StateFlow<SharedFeedUiState> = _state.asStateFlow()

    /** Noms d'amis (id → nom) résolus une fois par (re)chargement, réutilisés en pagination. */
    private var friendNames: Map<String, String> = emptyMap()

    init {
        load()
    }

    /** (Re)charge la première page du feed, en repartant de zéro. */
    fun load() = fetchFirstPage(isRefresh = false)

    /**
     * Rechargement déclenché par le pull-to-refresh (TÂCHE 1.3) : même passe que
     * [load], mais la liste courante reste affichée avec l'indicateur de tirage
     * (pas d'écran « Chargement… » plein écran).
     */
    fun refresh() = fetchFirstPage(isRefresh = true)

    private fun fetchFirstPage(isRefresh: Boolean) {
        val sort = _state.value.sort
        _state.update {
            it.copy(
                loading = !isRefresh,
                refreshing = isRefresh,
                error = null,
                endReached = false,
            )
        }
        viewModelScope.launch {
            try {
                val flowers = feedApi.getFeed(sort = sort.apiValue, limit = PAGE_SIZE)
                friendNames = friendshipsApi.list()
                    .associate { it.user.id to it.user.displayName }
                _state.value = SharedFeedUiState(
                    loading = false,
                    items = flowers.map { SharedFlowerItem(it, friendNames[it.ownerId]) },
                    sort = sort,
                    // Le curseur `before` ne vaut que pour le tri par date ; en tri
                    // par cœurs on ne pagine pas (fin atteinte d'emblée).
                    endReached = sort != FeedSort.DATE || flowers.size < PAGE_SIZE,
                )
                // Ouvrir l'onglet « voit » les fleurs courantes du feed : le badge
                // de nouveautés de la bottom bar revient à 0.
                badgeStore?.markSeen(flowers.map { it.id }.toSet())
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "Erreur réseau. Réessayez.",
                    )
                }
            }
        }
    }

    /**
     * Charge la page suivante à l'approche du bas de liste (pagination keyset,
     * TÂCHE 1.2). No-op si un chargement est déjà en cours, si la fin est atteinte
     * ou hors tri par date (le curseur `before` y est réservé).
     */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        if (current.sort != FeedSort.DATE) return
        val last = current.items.lastOrNull() ?: return
        // Curseur = repère (createdAt, id) de la dernière fleur affichée.
        val cursor = "${last.flower.createdAt}_${last.flower.id}"
        _state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            try {
                val flowers = feedApi.getFeed(
                    sort = FeedSort.DATE.apiValue,
                    limit = PAGE_SIZE,
                    before = cursor,
                )
                val knownIds = current.items.map { it.flower.id }.toSet()
                // Déduplique par sécurité (une fleur re-partagée entre-temps).
                val newItems = flowers
                    .filter { it.id !in knownIds }
                    .map { SharedFlowerItem(it, friendNames[it.ownerId]) }
                _state.update {
                    it.copy(
                        loadingMore = false,
                        items = it.items + newItems,
                        endReached = flowers.size < PAGE_SIZE,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(loadingMore = false, error = e.message ?: "Erreur réseau. Réessayez.")
                }
            }
        }
    }

    /** Change l'ordre du feed et recharge depuis la première page. */
    fun setSort(sort: FeedSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        load()
    }

    /**
     * Bascule le cœur d'une fleur avec mise à jour optimiste (NODE-140) : l'UI
     * reflète immédiatement le nouvel état, puis l'appel réseau confirme ou,
     * en cas d'échec, restaure l'état précédent.
     */
    fun toggleLike(flowerId: String) {
        val current = _state.value.items.find { it.flower.id == flowerId } ?: return
        val wasLiked = current.flower.likedByMe
        applyLike(flowerId, liked = !wasLiked)
        viewModelScope.launch {
            val response = runCatching {
                if (wasLiked) likesApi.unlike(flowerId) else likesApi.like(flowerId)
            }
            val ok = response.getOrNull()?.isSuccessful == true
            if (!ok) applyLike(flowerId, liked = wasLiked) // restaure
        }
    }

    /** Reflète l'état liké/non-liké d'une fleur dans la liste (compteur inclus). */
    private fun applyLike(flowerId: String, liked: Boolean) {
        _state.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.flower.id != flowerId) return@map item
                    if (item.flower.likedByMe == liked) return@map item
                    val delta = if (liked) 1 else -1
                    item.copy(
                        flower = item.flower.copy(
                            likedByMe = liked,
                            likeCount = (item.flower.likeCount + delta).coerceAtLeast(0),
                        ),
                    )
                },
            )
        }
    }

    companion object {
        /** Taille d'une page du feed (pagination keyset, TÂCHE 1.2). */
        private const val PAGE_SIZE = 20

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return SharedFeedViewModel(
                        apis.feed,
                        apis.friendships,
                        apis.likes,
                        FeedBadgeStore(context.applicationContext),
                    ) as T
                }
            }
    }
}
