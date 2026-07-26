package com.florapin.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.MyIdentificationRequestDto
import com.florapin.app.network.dto.SpeciesDto
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.desktop.app.AppModel
import com.florapin.desktop.ui.AsyncPhoto
import com.florapin.desktop.ui.EmptyState
import kotlinx.coroutines.delay

/**
 * Identification collaborative — la fonctionnalité sociale du produit, reprise
 * intégralement sur le compagnon.
 *
 * Deux colonnes plutôt que deux onglets : aider ses amis et suivre ses propres
 * demandes sont deux activités qu'on alterne, et l'écran est assez large pour
 * les tenir côte à côte.
 */
@Composable
fun IdentifyScreen(model: AppModel) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ColumnHeader(
                title = "On vous demande de l'aide",
                subtitle = "Fleurs que vos amis cherchent à identifier",
            )
            HorizontalDivider()
            if (model.toIdentify.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
                        title = "Rien à identifier",
                        hint = "Quand un ami sollicitera votre avis, sa fleur " +
                            "apparaîtra ici.",
                        icon = Icons.Filled.Spa,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(model.toIdentify, key = { it.id }) { flower ->
                        ProposeCard(model, flower)
                    }
                }
            }
        }

        HorizontalDivider(Modifier.fillMaxHeight().width(1.dp))

        Column(Modifier.weight(1f).fillMaxHeight()) {
            ColumnHeader(
                title = "Vos demandes",
                subtitle = "Propositions reçues sur vos fleurs",
            )
            HorizontalDivider()
            if (model.myIdentificationRequests.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    EmptyState(
                        title = "Aucune demande en cours",
                        hint = "Depuis la photothèque, sélectionnez une fleur et " +
                            "demandez l'aide de vos amis.",
                        icon = Icons.Filled.Spa,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(model.myIdentificationRequests, key = { it.flower.id }) { request ->
                        RequestCard(model, request)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(title: String, subtitle: String) {
    Surface(tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fleur d'un ami : proposer une espèce, avec suggestions du référentiel. */
@Composable
private fun ProposeCard(model: AppModel, flower: FlowerDto) {
    var species by remember(flower.id) { mutableStateOf("") }
    var suggestions by remember(flower.id) { mutableStateOf<List<SpeciesDto>>(emptyList()) }
    var menuOpen by remember(flower.id) { mutableStateOf(false) }

    // Autocomplétion différée : sans ce délai, chaque frappe déclencherait une
    // requête, pour un résultat qui change à peine.
    LaunchedEffect(species) {
        if (species.length < 2) {
            suggestions = emptyList()
            menuOpen = false
        } else {
            delay(250)
            suggestions = model.searchSpecies(species)
            menuOpen = suggestions.isNotEmpty()
        }
    }

    Card {
        Row(Modifier.padding(12.dp)) {
            AsyncPhoto(
                url = flower.previewPhotoUrls().firstOrNull(),
                modifier = Modifier.size(120.dp).clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = flower.ownerName.ifBlank { "Un ami" },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = formatDate(flower.takenAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (flower.notes.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = flower.notes,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Box {
                    OutlinedTextField(
                        value = species,
                        onValueChange = { species = it },
                        label = { Text("Votre proposition") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        // Le menu ne doit pas voler le focus au champ, sinon la
                        // saisie s'interrompt à chaque suggestion.
                        properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                    ) {
                        suggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(suggestion.commonName)
                                        Text(
                                            text = suggestion.scientificName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    species = suggestion.commonName
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        model.proposeSpecies(flower.id, species)
                        species = ""
                    },
                    enabled = species.isNotBlank(),
                ) { Text("Proposer") }
            }
        }
    }
}

/** Ma fleur en attente : propositions reçues, à accepter ou refuser. */
@Composable
private fun RequestCard(model: AppModel, request: MyIdentificationRequestDto) {
    val flower = request.flower
    Card {
        Column(Modifier.padding(12.dp)) {
            Row {
                AsyncPhoto(
                    url = flower.previewPhotoUrls().firstOrNull(),
                    modifier = Modifier.size(90.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(formatDate(flower.takenAt), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (request.proposals.isEmpty()) {
                            "Aucune proposition pour l'instant"
                        } else {
                            "${request.proposals.size} proposition(s)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (flower.needsIdentification) {
                        TextButton(onClick = { model.cancelIdentification(flower.id) }) {
                            Text("Annuler la demande")
                        }
                    }
                }
            }

            request.proposals.forEach { proposal ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(proposal.species, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = buildString {
                                append("par ${proposal.proposedByName.ifBlank { "un ami" }}")
                                if (proposal.status == "accepted") append("  ·  acceptée")
                                if (proposal.thankedAt != null) append("  ·  remerciée")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (proposal.status != "accepted") {
                        OutlinedButton(
                            onClick = { model.acceptProposal(flower.id, proposal.id) },
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(Icons.Outlined.Check, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Accepter")
                        }
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = { model.rejectProposal(flower.id, proposal.id) }) {
                            Icon(Icons.Outlined.Close, null, Modifier.size(15.dp))
                        }
                    } else if (proposal.thankedAt == null) {
                        TextButton(onClick = { model.thankProposal(flower.id, proposal.id) }) {
                            Icon(Icons.Outlined.Favorite, null, Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Merci")
                        }
                    }
                }
            }
        }
    }
}
