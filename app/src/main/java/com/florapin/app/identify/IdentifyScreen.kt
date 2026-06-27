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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.fullPhotoUrls
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.app.ui.components.EmptyState
import com.florapin.app.ui.components.PhotoCarousel

/**
 * Écran « Fleurs à identifier » (NODE-134) : les fleurs non identifiées que mes
 * amis m'ont partagées, avec saisie d'une proposition d'espèce par fleur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IdentifyViewModel = viewModel(
        factory = IdentifyViewModel.factory(LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Fleurs à identifier") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("Chargement…") }

                state.error != null -> EmptyState(
                    title = "Erreur",
                    message = state.error ?: "",
                )

                state.flowers.isEmpty() -> EmptyState(
                    title = "Rien à identifier",
                    message = "Aucun ami ne vous a sollicité pour le moment.",
                )

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
                        )
                    }
                }
            }
        }
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
) {
    var species by remember(flower.id) { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
        }
    }
}
