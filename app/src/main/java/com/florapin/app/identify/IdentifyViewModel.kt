package com.florapin.app.identify

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.ProposeSpeciesRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'écran « Fleurs à identifier » (NODE-134). */
data class IdentifyUiState(
    val loading: Boolean = true,
    /** Rechargement via pull-to-refresh (liste déjà visible), distinct du chargement initial. */
    val refreshing: Boolean = false,
    val flowers: List<FlowerDto> = emptyList(),
    val error: String? = null,
    /** Fleurs pour lesquelles une proposition a été envoyée durant la session. */
    val proposedIds: Set<String> = emptySet(),
    /** Fleurs dont l'envoi de proposition est en cours. */
    val submittingIds: Set<String> = emptySet(),
    /** Erreurs d'envoi par fleur. */
    val submitErrors: Map<String, String> = emptyMap(),
)

/**
 * Fleurs « à identifier » partagées avec moi : liste (GET /identification-requests)
 * et envoi d'une proposition d'espèce (POST flowers/{id}/proposals) par ami.
 */
class IdentifyViewModel(
    private val api: IdentificationApi,
    /** Suivi des demandes vues (badge). Null en test : la remise à 0 est ignorée. */
    private val badgeStore: IdentifyBadgeStore? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(IdentifyUiState())
    val state: StateFlow<IdentifyUiState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * (Re)charge la liste des fleurs à identifier. Avec [isRefresh] = true
     * (pull-to-refresh, TÂCHE 1.3), la liste courante reste affichée sous
     * l'indicateur de tirage plutôt que de basculer sur l'écran « Chargement… ».
     */
    fun load(isRefresh: Boolean = false) {
        _state.update { it.copy(loading = !isRefresh, refreshing = isRefresh, error = null) }
        viewModelScope.launch {
            try {
                val flowers = api.listToIdentify()
                _state.update {
                    it.copy(loading = false, refreshing = false, flowers = flowers, error = null)
                }
                // Ouvrir l'écran « voit » toutes les demandes courantes : le badge
                // de la galerie revient à 0, même sans avoir proposé d'espèce.
                badgeStore?.markSeen(flowers.map { it.id }.toSet())
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = e.message ?: "Impossible de charger les demandes.",
                    )
                }
            }
        }
    }

    /** Rechargement déclenché par le pull-to-refresh (TÂCHE 1.3). */
    fun refresh() = load(isRefresh = true)

    /** Propose une espèce pour la fleur [flowerId]. Ignore les saisies vides. */
    fun propose(flowerId: String, species: String) {
        val trimmed = species.trim()
        if (trimmed.isEmpty()) return
        if (flowerId in _state.value.submittingIds) return
        _state.update {
            it.copy(
                submittingIds = it.submittingIds + flowerId,
                submitErrors = it.submitErrors - flowerId,
            )
        }
        viewModelScope.launch {
            try {
                api.propose(flowerId, ProposeSpeciesRequest(trimmed))
                _state.update {
                    it.copy(
                        submittingIds = it.submittingIds - flowerId,
                        proposedIds = it.proposedIds + flowerId,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        submittingIds = it.submittingIds - flowerId,
                        submitErrors = it.submitErrors +
                            (flowerId to (e.message ?: "Échec de l'envoi.")),
                    )
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
                    return IdentifyViewModel(
                        apis.identification,
                        IdentifyBadgeStore(context.applicationContext),
                    ) as T
                }
            }
    }
}
