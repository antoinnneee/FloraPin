package com.florapin.app.identify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.detail.CommentsBottomSheet
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.IdentificationStatus
import com.florapin.app.network.dto.MyIdentificationRequestDto
import com.florapin.app.network.dto.fullPhotoUrls
import com.florapin.app.network.dto.identificationStatus
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.app.ui.components.EmptyState
import com.florapin.app.ui.components.PhotoCarousel

/**
 * Écran d'identification collaborative avec deux onglets :
 * - « À identifier » (NODE-134) : les fleurs non identifiées que mes amis m'ont
 *   partagées, avec saisie d'une proposition d'espèce par fleur ;
 * - « Mes demandes » (TÂCHE 4.1) : l'état de mes propres sollicitations, mes
 *   fleurs en attente et « qui a proposé quoi ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IdentifyViewModel = viewModel(
        factory = IdentifyViewModel.factory(LocalContext.current),
    ),
    myRequestsViewModel: MyRequestsViewModel = viewModel(
        factory = MyRequestsViewModel.factory(LocalContext.current),
    ),
) {
    // Fleur dont le fil de discussion est ouvert (bottom sheet), ou null.
    // Partagé par les deux onglets.
    var commentsFor by remember { mutableStateOf<String?>(null) }
    commentsFor?.let { flowerId ->
        CommentsBottomSheet(
            flowerServerId = flowerId,
            onDismiss = { commentsFor = null },
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("À identifier", "Mes demandes")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Identification") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) },
                    )
                }
            }
            when (selectedTab) {
                0 -> ToIdentifyTab(
                    viewModel = viewModel,
                    onComment = { commentsFor = it },
                )
                else -> MyRequestsTab(
                    viewModel = myRequestsViewModel,
                    onComment = { commentsFor = it },
                )
            }
        }
    }
}

/**
 * Onglet « À identifier » (NODE-134) : les fleurs non identifiées que mes amis
 * m'ont partagées, avec saisie d'une proposition d'espèce par fleur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToIdentifyTab(
    viewModel: IdentifyViewModel,
    onComment: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Chargement…") }

            state.error != null -> RefreshableEmpty {
                EmptyState(
                    title = "Erreur",
                    message = state.error ?: "",
                )
            }

            state.flowers.isEmpty() -> RefreshableEmpty {
                EmptyState(
                    title = "Rien à identifier",
                    message = "Aucun ami ne vous a sollicité pour le moment.",
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.flowers, key = { it.id }) { flower ->
                    IdentifyCard(
                        flower = flower,
                        proposed = flower.id in state.proposedIds,
                        submitting = flower.id in state.submittingIds,
                        error = state.submitErrors[flower.id],
                        onPropose = { species -> viewModel.propose(flower.id, species) },
                        onComment = { onComment(flower.id) },
                    )
                }
            }
        }
    }
}

/**
 * Onglet « Mes demandes » (TÂCHE 4.1) : l'état de mes propres sollicitations,
 * mes fleurs en attente et les propositions reçues (« qui a proposé quoi »).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyRequestsTab(
    viewModel: MyRequestsViewModel,
    onComment: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text("Chargement…") }

            state.error != null -> RefreshableEmpty {
                EmptyState(
                    title = "Erreur",
                    message = state.error ?: "",
                )
            }

            state.requests.isEmpty() -> RefreshableEmpty {
                EmptyState(
                    title = "Aucune demande en cours",
                    message = "Ouvrez une fleur non identifiée et demandez à vos amis " +
                        "de vous aider à l'identifier.",
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.requests, key = { it.flower.id }) { request ->
                    MyRequestCard(
                        request = request,
                        reminding = request.flower.id in state.remindingIds,
                        reminded = request.flower.id in state.remindedIds,
                        remindError = state.remindErrors[request.flower.id],
                        onRemind = { viewModel.remind(request.flower.id) },
                        onComment = { onComment(request.flower.id) },
                    )
                }
            }
        }
    }
}

/**
 * Enveloppe un contenu « vide » (non défilable) dans une [LazyColumn] pleine zone,
 * afin que le pull-to-refresh reste déclenchable même sans liste à faire défiler.
 */
@Composable
private fun RefreshableEmpty(content: @Composable () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Box(modifier = Modifier.fillParentMaxSize()) { content() }
        }
    }
}

/**
 * Pastille de statut d'une demande d'identification (TÂCHE 4.2), affichée des deux
 * côtés : « En attente » tant que le propriétaire n'a pas tranché, « Résolue » une
 * fois qu'une proposition a été acceptée. Dérivée, sans colonne dédiée.
 */
@Composable
fun IdentificationStatusBadge(
    status: IdentificationStatus,
    modifier: Modifier = Modifier,
) {
    val (container, content, label) = when (status) {
        IdentificationStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "⏳ En attente",
        )
        IdentificationStatus.RESOLVED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "✅ Résolue",
        )
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Carte d'une fleur à identifier : aperçu + champ de proposition d'espèce. */
@Composable
private fun IdentifyCard(
    flower: FlowerDto,
    proposed: Boolean,
    submitting: Boolean,
    error: String?,
    onPropose: (String) -> Unit,
    onComment: () -> Unit,
) {
    var species by remember(flower.id) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Statut de la demande (TÂCHE 4.2) : l'ami voit si elle est toujours
            // ouverte ou déjà tranchée par le propriétaire.
            IdentificationStatusBadge(status = flower.identificationStatus())
            // Toutes les photos de la fleur (carrousel + plein écran/zoom au clic),
            // pour mieux juger l'espèce à proposer. Repli sur la couverture seule.
            PhotoCarousel(
                previewModels = flower.previewPhotoUrls(),
                fullModels = flower.fullPhotoUrls(),
                contentDescription = "Fleur à identifier",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
            if (flower.notes.isNotBlank()) {
                Text(
                    text = flower.notes,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (proposed) {
                Text(
                    text = "✅ Proposition envoyée. Merci !",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                OutlinedTextField(
                    value = species,
                    onValueChange = { species = it },
                    label = { Text("Quelle espèce ?") },
                    singleLine = true,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = { onPropose(species) },
                    enabled = species.isNotBlank() && !submitting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (submitting) "Envoi…" else "Proposer cette espèce")
                }
            }

            // Discussion autour de la demande : poser des questions sur le milieu,
            // demander une photo supplémentaire… (le fil est partagé avec le
            // propriétaire et le réseau d'amis sollicité).
            TextButton(
                onClick = onComment,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("💬 Discuter (environnement, photos…)")
            }
        }
    }
}

/**
 * Carte d'une de MES demandes (TÂCHE 4.1) : aperçu de ma fleur en attente et la
 * liste des propositions reçues (« qui a proposé quoi »). Vue d'état : l'accept /
 * refus d'une proposition se fait depuis le détail de la fleur.
 */
@Composable
private fun MyRequestCard(
    request: MyIdentificationRequestDto,
    reminding: Boolean,
    reminded: Boolean,
    remindError: String?,
    onRemind: () -> Unit,
    onComment: () -> Unit,
) {
    val flower = request.flower

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Statut de ma demande (TÂCHE 4.2), dérivé de l'état de la fleur et des
            // propositions reçues : « En attente » ou « Résolue ».
            IdentificationStatusBadge(status = request.identificationStatus())
            PhotoCarousel(
                previewModels = flower.previewPhotoUrls(),
                fullModels = flower.fullPhotoUrls(),
                contentDescription = "Ma fleur en attente d'identification",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
            if (flower.notes.isNotBlank()) {
                Text(
                    text = flower.notes,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val count = request.proposals.size
            if (count == 0) {
                Text(
                    text = "⏳ En attente d'une proposition de vos amis…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = if (count == 1) "1 proposition reçue" else "$count propositions reçues",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                request.proposals.forEach { proposal ->
                    Text(
                        text = "🌿 ${proposal.species} — proposé par ${
                            proposal.proposedByName.ifBlank { "un ami" }
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = "Ouvrez la fleur pour accepter ou refuser une proposition.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Relance manuelle (TÂCHE 4.4) : re-sollicite le réseau d'amis.
            // L'anti-spam est arbitré côté serveur (409) — restitué en message.
            if (reminded) {
                Text(
                    text = "✅ Amis relancés. Merci de patienter un peu !",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                OutlinedButton(
                    onClick = onRemind,
                    enabled = !reminding,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (reminding) "Relance…" else "🔔 Relancer mes amis")
                }
                remindError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            TextButton(
                onClick = onComment,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("💬 Discuter (environnement, photos…)")
            }
        }
    }
}
