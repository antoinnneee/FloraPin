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
    val items: List<SharedFlowerItem> = emptyList(),
    val error: String? = null,
    val sort: FeedSort = FeedSort.DATE,
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
) : ViewModel() {

    private val _state = MutableStateFlow(SharedFeedUiState(loading = true))
    val state: StateFlow<SharedFeedUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val sort = _state.value.sort
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val flowers = feedApi.getFeed(sort = sort.apiValue)
                val names = friendshipsApi.list().associate { it.user.id to it.user.displayName }
                _state.value = SharedFeedUiState(
                    loading = false,
                    items = flowers.map { SharedFlowerItem(it, names[it.ownerId]) },
                    sort = sort,
                )
            } catch (e: Exception) {
                _state.update {
                    it.copy(loading = false, error = e.message ?: "Erreur réseau. Réessayez.")
                }
            }
        }
    }

    /** Change l'ordre du feed et recharge. */
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
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return SharedFeedViewModel(apis.feed, apis.friendships, apis.likes) as T
                }
            }
    }
}
