package com.florapin.app.likes

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.LikesApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.LikerDto
import com.florapin.app.network.dto.Reactions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de la liste des personnes ayant liké une fleur (NODE-140). */
data class LikersUiState(
    val likers: List<LikerDto> = emptyList(),
    val loading: Boolean = false,
)

/**
 * Charge la liste des likers d'une fleur (`GET flowers/{id}/likes`). Comme le fil
 * de commentaires, ces données collaboratives vivent côté serveur ; une fleur non
 * accessible (403/404) dégrade en liste vide plutôt qu'en erreur.
 */
class LikersViewModel(
    private val api: LikesApi,
) : ViewModel() {

    private val _state = MutableStateFlow(LikersUiState())
    val state: StateFlow<LikersUiState> = _state.asStateFlow()

    private var serverId: String? = null

    /** Associe la fleur [flowerServerId] (idempotent) et charge ses likers. */
    fun bind(flowerServerId: String) {
        if (serverId == flowerServerId && _state.value.likers.isNotEmpty()) return
        serverId = flowerServerId
        load()
    }

    /** (Re)charge la liste des likers de la fleur liée. */
    fun load() {
        val id = serverId ?: return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            _state.value = try {
                LikersUiState(likers = api.likers(id), loading = false)
            } catch (e: Exception) {
                LikersUiState(likers = emptyList(), loading = false)
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
                    return LikersViewModel(apis.likes) as T
                }
            }
    }
}

/**
 * Bottom sheet listant les personnes ayant liké une fleur, ouvert au tap sur le
 * compteur de cœurs (détail comme feed). La clé [flowerServerId] isole un
 * ViewModel par fleur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikersBottomSheet(
    flowerServerId: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val likersVm: LikersViewModel = viewModel(
        key = "likers-$flowerServerId",
        factory = LikersViewModel.factory(LocalContext.current),
    )
    LaunchedEffect(flowerServerId) {
        likersVm.bind(flowerServerId)
    }
    val state by likersVm.state.collectAsStateWithLifecycle()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (state.likers.isEmpty()) {
                    "❤️ Likes"
                } else {
                    "❤️ Likes (${state.likers.size})"
                },
                style = MaterialTheme.typography.titleMedium,
            )

            when {
                state.loading && state.likers.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.likers.isEmpty() -> {
                    Text(
                        text = "Personne n'a encore liké cette fleur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> {
                    state.likers.forEach { liker ->
                        Text(
                            text = "${Reactions.emoji(liker.reaction)} " +
                                liker.displayName.ifBlank { "Quelqu'un" },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
