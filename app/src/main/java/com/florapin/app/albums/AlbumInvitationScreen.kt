package com.florapin.app.albums

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.R
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.api.GroupsApi
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.GroupDto
import com.florapin.app.sync.SyncScheduler
import com.florapin.app.ui.components.BotanicalIcon
import com.florapin.app.ui.components.networkErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AlbumInvitationUiState(
    val loading: Boolean = true,
    val accepting: Boolean = false,
    val accepted: Boolean = false,
    val group: GroupDto? = null,
    val error: String? = null,
)

/**
 * Page de prévisualisation d'un album reçu. Elle est volontairement située dans
 * le parcours Albums : ouvrir une invitation montre l'album avant de demander
 * l'acceptation, au lieu de renvoyer vers une page sociale générique.
 */
class AlbumInvitationViewModel(
    private val groupId: String,
    private val groupsApi: GroupsApi,
    private val onAccepted: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(AlbumInvitationUiState())
    val state: StateFlow<AlbumInvitationUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val group = groupsApi.get(groupId)
                _state.update {
                    it.copy(
                        loading = false,
                        group = group,
                        accepted = group.status == "accepted",
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = networkErrorMessage(e)) }
            }
        }
    }

    fun accept() {
        if (_state.value.accepting || _state.value.accepted) return
        _state.update { it.copy(accepting = true, error = null) }
        viewModelScope.launch {
            try {
                val group = groupsApi.accept(groupId)
                _state.update {
                    it.copy(accepting = false, accepted = true, group = group)
                }
                onAccepted()
            } catch (e: Exception) {
                _state.update {
                    it.copy(accepting = false, error = networkErrorMessage(e))
                }
            }
        }
    }

    companion object {
        fun factory(context: Context, groupId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val appContext = context.applicationContext
                    val api = NetworkModule.createAuthenticated(
                        EncryptedTokenStore(appContext),
                    ).groups
                    return AlbumInvitationViewModel(
                        groupId = groupId,
                        groupsApi = api,
                        onAccepted = {
                            SyncScheduler.syncNow(appContext, force = true)
                        },
                    ) as T
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumInvitationScreen(
    groupId: String,
    onBack: () -> Unit,
    onAccepted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumInvitationViewModel = viewModel(
        key = "album-invitation-$groupId",
        factory = AlbumInvitationViewModel.factory(LocalContext.current, groupId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.accepted) {
        if (state.accepted) onAccepted()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Album partagé") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BotanicalIcon(R.drawable.ic_back_botanical, "Retour")
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            state.group != null -> InvitationContent(
                group = state.group!!,
                accepting = state.accepting,
                accepted = state.accepted,
                error = state.error,
                onAccept = viewModel::accept,
                modifier = Modifier.padding(innerPadding),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Impossible d'ouvrir cet album", style = MaterialTheme.typography.titleLarge)
                Text(
                    state.error ?: "L'invitation n'est plus disponible.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(onClick = viewModel::load, modifier = Modifier.padding(top = 20.dp)) {
                    Text("Réessayer")
                }
            }
        }
    }
}

@Composable
private fun InvitationContent(
    group: GroupDto,
    accepting: Boolean,
    accepted: Boolean,
    error: String?,
    onAccept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                BotanicalIcon(
                    iconRes = R.drawable.ic_album_option_04_leaf,
                    contentDescription = null,
                    size = 92.dp,
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                ) {
                    Text(
                        text = "Herbier collaboratif",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        Text(
            text = group.name,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp),
        )
        Text(
            text = "On vous invite à observer, photographier et enrichir cet album ensemble.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(
            modifier = Modifier.padding(top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy((-8).dp),
        ) {
            group.members.take(5).forEach { member ->
                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            member.displayName.firstOrNull()?.uppercase() ?: "•",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
        Text(
            text = "${group.members.count { it.status == "accepted" }} membre" +
                if (group.members.count { it.status == "accepted" } > 1) "s" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.weight(1f))
        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Button(
            onClick = onAccept,
            enabled = !accepting && !accepted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            if (accepting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(if (accepted) "Album accepté" else "Rejoindre cet album")
            }
        }
    }
}
