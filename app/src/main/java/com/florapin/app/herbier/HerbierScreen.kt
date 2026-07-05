package com.florapin.app.herbier

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.data.LocalSpeciesCount
import com.florapin.app.network.dto.HerbierFamilyDto
import com.florapin.app.network.dto.HerbierSpeciesDto

/**
 * Écran « Mon herbier » (TÂCHE 5.6) : nombre d'espèces distinctes et regroupement
 * par familles botaniques.
 *
 * Device-first : les compteurs d'en-tête et la liste des espèces viennent de la
 * base locale (toujours disponibles). Le volet familles vient du serveur (la
 * famille est portée par l'espèce) : hors-ligne il est indisponible, on retombe
 * alors sur la liste à plat des espèces locales avec un bandeau explicatif.
 *
 * Un tap sur une espèce rapprochée au référentiel ([HerbierSpeciesDto.id] non
 * null) ouvre sa fiche.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HerbierScreen(
    onBack: () -> Unit,
    onOpenSpecies: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HerbierViewModel = viewModel(
        factory = HerbierViewModel.factory(LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Mon herbier") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HerbierHeader(state) }

            if (state.familiesAvailable) {
                item {
                    Text(
                        text = "Familles botaniques",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (state.families.isEmpty() && !state.loading) {
                    item { EmptyHerbierNotice() }
                }
                items(state.families, key = { it.family }) { family ->
                    FamilyCard(family = family, onOpenSpecies = onOpenSpecies)
                }
            } else {
                item { FamiliesOfflineNotice() }
                item {
                    Text(
                        text = "Mes espèces",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (state.localSpecies.isEmpty() && !state.loading) {
                    item { EmptyHerbierNotice() }
                }
                items(state.localSpecies) { entry ->
                    LocalSpeciesRow(entry)
                }
            }
        }
    }
}

/** En-tête : espèces distinctes + fleurs de la collection (compteurs locaux). */
@Composable
private fun HerbierHeader(state: HerbierUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatColumn(value = state.distinctSpecies, label = "espèces")
            StatColumn(value = state.totalFlowers, label = "fleurs")
            StatColumn(value = state.familyCountForDisplay(), label = "familles")
        }
    }
}

@Composable
private fun StatColumn(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Carte d'une famille, dépliable pour révéler ses espèces. */
@Composable
private fun FamilyCard(
    family: HerbierFamilyDto,
    onOpenSpecies: (String) -> Unit,
) {
    // Mémorise l'état déplié par famille (clé stable = nom de famille).
    val expandedByFamily = remember { mutableStateMapOf<String, Boolean>() }
    val expanded = expandedByFamily[family.family] ?: false

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expandedByFamily[family.family] = !expanded
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = family.family,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = speciesCountLabel(family.speciesCount) +
                            " · " + flowerCountLabel(family.flowerCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(if (expanded) "▾" else "▸")
            }

            if (expanded) {
                HorizontalDivider()
                family.species.forEach { species ->
                    SpeciesRow(species = species, onOpenSpecies = onOpenSpecies)
                }
            }
        }
    }
}

/** Ligne d'une espèce dans une famille dépliée (tap → fiche si rapprochée). */
@Composable
private fun SpeciesRow(
    species: HerbierSpeciesDto,
    onOpenSpecies: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (species.id != null) {
                    Modifier.clickable { onOpenSpecies(species.id) }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = species.emoji ?: "🌸")
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = species.scientificName.ifBlank { "Espèce inconnue" },
                style = MaterialTheme.typography.bodyLarge,
            )
            if (species.commonName.isNotBlank()) {
                Text(
                    text = species.commonName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = species.flowerCount.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ligne d'espèce du repli local à plat (hors-ligne, sans famille). */
@Composable
private fun LocalSpeciesRow(entry: LocalSpeciesCount) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "🌸")
            Text(
                text = entry.name?.ifBlank { "Espèce inconnue" } ?: "Espèce inconnue",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = entry.flowerCount.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Bandeau expliquant que le regroupement par familles nécessite une connexion
 * (dégradation propre device-first, pattern `CommentsLockedNotice`).
 */
@Composable
private fun FamiliesOfflineNotice() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Regroupement par familles indisponible hors ligne",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Les familles botaniques sont calculées sur le serveur. " +
                    "Reconnectez-vous pour les afficher ; en attendant, voici vos " +
                    "espèces à plat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHerbierNotice() {
    Text(
        text = "Aucune espèce pour le moment. Identifiez vos fleurs pour " +
            "constituer votre herbier.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Nombre de familles à afficher dans l'en-tête : côté serveur si disponible
 * (« Non classées » exclue), sinon on masque le compteur (0) faute de familles.
 */
private fun HerbierUiState.familyCountForDisplay(): Int =
    if (familiesAvailable) {
        families.count { it.family != "Non classées" }
    } else {
        0
    }

private fun speciesCountLabel(count: Int): String =
    if (count == 1) "1 espèce" else "$count espèces"

private fun flowerCountLabel(count: Int): String =
    if (count == 1) "1 fleur" else "$count fleurs"
