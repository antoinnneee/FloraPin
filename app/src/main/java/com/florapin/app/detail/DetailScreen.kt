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
import com.florapin.app.location.GeoPoint
import com.florapin.app.util.formatCaptureDate
import java.io.File

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Détail") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
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
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun DetailContent(
    flower: FlowerEntity,
    onSaveNotes: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = File(flower.imagePath),
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

            NotesEditor(
                flowerId = flower.id,
                initialNotes = flower.notes,
                onSave = onSaveNotes,
            )
        }
    }
}

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
