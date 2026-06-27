package com.florapin.app.detail

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.IdentificationApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.SpeciesProposalDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de la section « Propositions reçues » (NODE-134, côté propriétaire). */
data class ReceivedProposalsUiState(
    val proposals: List<SpeciesProposalDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** Proposition dont l'acceptation est en cours. */
    val acceptingId: String? = null,
)

/**
 * Propositions d'espèce reçues par le propriétaire sur une de ses fleurs
 * (NODE-134). Charge en direct depuis le serveur (`GET flowers/{id}/proposals`)
 * et permet d'en accepter une (`POST .../accept`). Indépendant de la sync
 * device-first : ces données collaboratives vivent côté serveur.
 */
class ReceivedProposalsViewModel(
    private val api: IdentificationApi,
) : ViewModel() {

    private val _state = MutableStateFlow(ReceivedProposalsUiState())
    val state: StateFlow<ReceivedProposalsUiState> = _state.asStateFlow()

    /** (Re)charge les propositions reçues pour la fleur [flowerServerId]. */
    fun load(flowerServerId: String) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            _state.value = try {
                val proposals = api.listProposals(flowerServerId)
                    .filter { it.status == "pending" }
                _state.value.copy(loading = false, proposals = proposals, error = null)
            } catch (e: Exception) {
                // Une fleur d'un autre que soi renvoie 404 : on masque simplement.
                _state.value.copy(loading = false, proposals = emptyList(), error = null)
            }
        }
    }

    /**
     * Accepte [proposal] : applique l'espèce côté serveur puis notifie l'appelant
     * via [onAccepted] pour répercuter l'espèce sur la fleur locale (device-first).
     */
    fun accept(
        flowerServerId: String,
        proposal: SpeciesProposalDto,
        onAccepted: (String) -> Unit,
    ) {
        if (_state.value.acceptingId != null) return
        _state.update { it.copy(acceptingId = proposal.id, error = null) }
        viewModelScope.launch {
            try {
                api.acceptProposal(flowerServerId, proposal.id)
                onAccepted(proposal.species)
                _state.update {
                    it.copy(
                        acceptingId = null,
                        proposals = it.proposals.filterNot { p -> p.id == proposal.id },
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        acceptingId = null,
                        error = e.message ?: "Échec de l'acceptation.",
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
                    return ReceivedProposalsViewModel(apis.identification) as T
                }
            }
    }
}

/**
 * Section « Propositions reçues » affichée sur le détail d'une fleur non
 * identifiée du propriétaire : une carte par proposition (espèce + bouton
 * « Accepter »). Masquée s'il n'y a aucune proposition en attente.
 */
@Composable
fun ReceivedProposalsSection(
    viewModel: ReceivedProposalsViewModel,
    onAccept: (SpeciesProposalDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.proposals.isEmpty() && state.error == null) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Propositions de vos amis",
            style = MaterialTheme.typography.titleMedium,
        )
        state.proposals.forEach { proposal ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "🌿 ${proposal.species}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(
                        onClick = { onAccept(proposal) },
                        enabled = state.acceptingId == null,
                    ) {
                        Text(if (state.acceptingId == proposal.id) "…" else "Accepter")
                    }
                }
            }
        }
        state.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
