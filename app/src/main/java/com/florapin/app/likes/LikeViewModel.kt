package com.florapin.app.likes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.api.LikesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État du cœur d'une fleur sur l'écran de détail (NODE-140). */
data class LikeState(
    val liked: Boolean = false,
    val count: Int = 0,
    val loaded: Boolean = false,
)

/**
 * Cœur d'une fleur sur DetailScreen : charge l'état distant (GET /flowers/{id})
 * et bascule le cœur en optimiste (POST/DELETE /flowers/{id}/like).
 */
class LikeViewModel(
    private val flowersApi: FlowersApi,
    private val likesApi: LikesApi,
) : ViewModel() {

    private val _state = MutableStateFlow(LikeState())
    val state: StateFlow<LikeState> = _state.asStateFlow()

    private var serverId: String? = null

    /** Associe la fleur (idempotent) et charge son état de cœur. */
    fun bind(flowerServerId: String) {
        if (serverId == flowerServerId && _state.value.loaded) return
        serverId = flowerServerId
        viewModelScope.launch {
            runCatching { flowersApi.get(flowerServerId) }.getOrNull()?.let {
                _state.value = LikeState(it.likedByMe, it.likeCount, loaded = true)
            }
        }
    }

    /** Bascule le cœur avec mise à jour optimiste, restaurée si l'appel échoue. */
    fun toggle() {
        val id = serverId ?: return
        val previous = _state.value
        val target = !previous.liked
        _state.value = previous.copy(
            liked = target,
            count = (previous.count + if (target) 1 else -1).coerceAtLeast(0),
        )
        viewModelScope.launch {
            val ok = runCatching {
                if (target) likesApi.like(id) else likesApi.unlike(id)
            }.getOrNull()?.isSuccessful == true
            if (!ok) _state.value = previous
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return LikeViewModel(apis.flowers, apis.likes) as T
                }
            }
    }
}
