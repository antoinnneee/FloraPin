package com.florapin.app.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.imageModel
import com.florapin.app.location.GeoPoint
import com.florapin.app.share.ShareFlowerSheet
import com.florapin.app.util.formatCaptureDate

/**
 * Détail d'une fleur (NODE-10) : photo, coordonnées, mini-carte, notes éditables
 * et suppression.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    flowerId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = viewModel(),
) {
    viewModel.setFlowerId(flowerId)
    val flower by viewModel.flower.collectAsStateWithLifecycle()
    var showShare by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Détail") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    val current = flower
                    if (current != null) {
                        IconButton(onClick = { showShare = true }) { Text("📤") }
                    }
                    IconButton(
                        onClick = { viewModel.delete(onDeleted = onBack) },
                    ) { Text("🗑️") }
                },
            )
        },
    ) { innerPadding ->
        val current = flower
        if (current == null) {
            // Soit en cours de chargement, soit supprimée (après quoi onBack a lieu).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Chargement…")
            }
        } else {
            DetailContent(
                flower = current,
                onSaveNotes = viewModel::saveNotes,
                onSaveClassification = viewModel::saveClassification,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (showShare && flower != null) {
        ShareFlowerSheet(
            flowerServerId = flower?.serverId,
            onDismiss = { showShare = false },
        )
    }
}

@Composable
private fun DetailContent(
    flower: FlowerEntity,
    onSaveNotes: (String) -> Unit,
    onSaveClassification: (String, List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = flower.imageModel(),
            contentDescription = "Photo de la fleur",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = formatCaptureDate(flower.createdAt),
                style = MaterialTheme.typography.titleMedium,
            )

            val point = flower.toGeoPoint()
            if (point != null) {
                Text(
                    text = "📍 ${point.format()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                MiniMap(point)
            } else {
                Text(
                    text = "📍 Position non enregistrée",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            ClassificationEditor(
                flowerId = flower.id,
                initialSpecies = flower.species.orEmpty(),
                initialTags = flower.tags,
                onSave = onSaveClassification,
            )

            NotesEditor(
                flowerId = flower.id,
                initialNotes = flower.notes,
                onSave = onSaveNotes,
            )
        }
    }
}

/** Édition de l'espèce et des étiquettes (étiquettes saisies séparées par des virgules). */
@Composable
private fun ClassificationEditor(
    flowerId: Long,
    initialSpecies: String,
    initialTags: List<String>,
    onSave: (String, List<String>) -> Unit,
) {
    val initialTagsText = initialTags.joinToString(", ")
    var species by remember(flowerId) { mutableStateOf(initialSpecies) }
    var tagsText by remember(flowerId) { mutableStateOf(initialTagsText) }

    val changed = species != initialSpecies || tagsText != initialTagsText

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = species,
            onValueChange = { species = it },
            label = { Text("Espèce") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = tagsText,
            onValueChange = { tagsText = it },
            label = { Text("Étiquettes (séparées par des virgules)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(species, parseTags(tagsText)) },
            enabled = changed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enregistrer espèce & étiquettes")
        }
    }
}

/** Découpe une saisie « a, b ,c » en liste nettoyée, sans doublons ni vides. */
private fun parseTags(raw: String): List<String> =
    raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Mini-carte (placeholder) : situe la position. Le rendu cartographique
 * interactif (MapLibre) est traité par la fonctionnalité Carte (NODE-11).
 */
@Composable
private fun MiniMap(point: GeoPoint) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("🗺️", style = MaterialTheme.typography.headlineMedium)
                Text(
                    text = "${"%.5f".format(point.latitude)}, " +
                        "%.5f".format(point.longitude),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Carte interactive à venir",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun NotesEditor(
    flowerId: Long,
    initialNotes: String,
    onSave: (String) -> Unit,
) {
    // Réinitialise le champ quand on change de fleur.
    var notes by remember(flowerId) { mutableStateOf(initialNotes) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Button(
            onClick = { onSave(notes) },
            enabled = notes != initialNotes,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enregistrer les notes")
        }
    }
}

/** Convertit les colonnes GPS en [GeoPoint], ou null si la position manque. */
private fun FlowerEntity.toGeoPoint(): GeoPoint? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return GeoPoint(lat, lng, accuracyMeters ?: 0f)
}
