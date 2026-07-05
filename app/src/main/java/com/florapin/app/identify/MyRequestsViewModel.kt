package com.florapin.app.identify

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.MyIdentificationRequestDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'onglet « Mes demandes » (TÂCHE 4.1). */
data class MyRequestsUiState(
    val loading: Boolean = true,
    /** Rechargement via pull-to-refresh (liste déjà visible), distinct du chargement initial. */
    val refreshing: Boolean = false,
    /** Mes fleurs en attente d'identification, avec les propositions reçues. */
    val requests: List<MyIdentificationRequestDto> = emptyList(),
    val error: String? = null,
)

/**
 * Onglet « Mes demandes » (TÂCHE 4.1) : l'état de mes propres sollicitations
 * d'identification — mes fleurs en attente et « qui a proposé quoi ».
 *
 * Le serveur compose fleurs + propositions en une requête
 * (GET /me/identification-requests) : aucune composition N+1 côté client.
 * Données collaboratives lues en direct du serveur, indépendantes de la sync
 * device-first (dégrade proprement hors-ligne via l'état d'erreur).
 */
class MyRequestsViewModel(
    private val api: IdentificationApi,
) : ViewModel() {

    private val _state = MutableStateFlow(MyRequestsUiState())
    val state: StateFlow<MyRequestsUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * (Re)charge mes demandes. Avec [isRefresh] = true (pull-to-refresh), la liste
     * courante reste affichée sous l'indicateur de tirage plutôt que de basculer
     * sur l'écran « Chargement… ».
     */
    fun load(isRefresh: Boolean = false) {
        _state.update { it.copy(loading = !isRefresh, refreshing = isRefresh, error = null) }
        viewModelScope.launch {
            _state.value = try {
                val requests = api.listMyRequests()
                _state.value.copy(
                    loading = false,
                    refreshing = false,
                    requests = requests,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = e.message ?: "Impossible de charger vos demandes.",
                )
            }
        }
    }

    /** Rechargement déclenché par le pull-to-refresh. */
    fun refresh() = load(isRefresh = true)

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val tokenStore = EncryptedTokenStore(context.applicationContext)
                    val apis = NetworkModule.createAuthenticated(tokenStore)
                    return MyRequestsViewModel(apis.identification) as T
                }
            }
    }
}
