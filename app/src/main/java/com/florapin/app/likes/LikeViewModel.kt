package com.florapin.app.likes

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.api.LikesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.ReactionRequest
import com.florapin.app.network.dto.Reactions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État de la réaction d'une fleur sur l'écran de détail (NODE-140 / TÂCHE 3.5). */
data class LikeState(
    /** Réaction du spectateur (code), ou null s'il n'a pas réagi. */
    val myReaction: String? = null,
    /** Nombre total de réactions, tous types confondus. */
    val count: Int = 0,
    /** Décompte par type de réaction (codes). */
    val reactionCounts: Map<String, Int> = emptyMap(),
    val loaded: Boolean = false,
)

/**
 * Applique optimiste le passage à la réaction [code] (ou null = retrait) : ajuste
 * « ma réaction », le total et le décompte par type. Changer de type conserve le
 * total (retire l'ancien, ajoute le nouveau).
 */
internal fun LikeState.withReaction(code: String?): LikeState {
    val old = myReaction
    if (old == code) return this
    val counts = reactionCounts.toMutableMap()
    if (old != null) {
        val n = (counts[old] ?: 1) - 1
        if (n > 0) counts[old] = n else counts.remove(old)
    }
    if (code != null) counts[code] = (counts[code] ?: 0) + 1
    val delta = (if (code != null) 1 else 0) - (if (old != null) 1 else 0)
    return copy(
        myReaction = code,
        count = (count + delta).coerceAtLeast(0),
        reactionCounts = counts,
    )
}

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

    /** Associe la fleur (idempotent) et charge son état de réaction. */
    fun bind(flowerServerId: String) {
        if (serverId == flowerServerId && _state.value.loaded) return
        serverId = flowerServerId
        viewModelScope.launch {
            runCatching { flowersApi.get(flowerServerId) }.getOrNull()?.let {
                _state.value = LikeState(
                    myReaction = it.myReaction,
                    count = it.likeCount,
                    reactionCounts = it.reactionCounts,
                    loaded = true,
                )
            }
        }
    }

    /**
     * Tap : bascule la réaction par défaut (cœur). S'il réagissait déjà (quel que
     * soit le type), le tap retire la réaction ; sinon il pose un cœur.
     */
    fun toggle() {
        if (_state.value.myReaction != null) remove() else react(Reactions.HEART)
    }

    /**
     * Pose (ou change) une réaction typée avec mise à jour optimiste, restaurée si
     * l'appel échoue. Changer d'emoji ne modifie pas le total (update, pas insert).
     */
    fun react(code: String) {
        val id = serverId ?: return
        val previous = _state.value
        _state.value = previous.withReaction(code)
        viewModelScope.launch {
            val ok = runCatching {
                likesApi.react(id, ReactionRequest(code))
            }.getOrNull()?.isSuccessful == true
            if (!ok) _state.value = previous
        }
    }

    /** Retire la réaction avec mise à jour optimiste, restaurée si l'appel échoue. */
    private fun remove() {
        val id = serverId ?: return
        val previous = _state.value
        _state.value = previous.withReaction(null)
        viewModelScope.launch {
            val ok = runCatching { likesApi.unlike(id) }
                .getOrNull()?.isSuccessful == true
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
