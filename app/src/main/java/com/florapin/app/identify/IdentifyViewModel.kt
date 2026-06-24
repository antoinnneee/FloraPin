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
) : ViewModel() {

    private val _state = MutableStateFlow(IdentifyUiState())
    val state: StateFlow<IdentifyUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** (Re)charge la liste des fleurs à identifier. */
    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val flowers = api.listToIdentify()
                _state.update { it.copy(loading = false, flowers = flowers, error = null) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Impossible de charger les demandes.",
                    )
                }
            }
        }
    }

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
                    return IdentifyViewModel(apis.identification) as T
                }
            }
    }
}
