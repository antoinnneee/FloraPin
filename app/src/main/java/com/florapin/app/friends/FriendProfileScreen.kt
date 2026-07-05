package com.florapin.app.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.FriendProfileDto
import com.florapin.app.network.dto.previewPhotoUrls
import com.florapin.app.ui.components.EmojiIcon
import com.florapin.app.ui.components.EmptyState
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Profil d'un ami (TÂCHE 5.7) : identité, ancienneté (« Amis depuis mai 2026 »),
 * amis en commun, espèces communes et fleurs de l'ami visibles par moi. Tout
 * provient de l'endpoint public limité `GET /users/:id/profile` : le serveur ne
 * renvoie que ce qui m'est déjà accessible, jamais les stats privées de l'ami.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendProfileScreen(
    userId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendProfileViewModel = viewModel(
        factory = FriendProfileViewModel.factory(
            androidx.compose.ui.platform.LocalContext.current,
            userId,
        ),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val profile = state.profile

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(profile?.displayName?.ifBlank { "Profil" } ?: "Profil") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        EmojiIcon("←", contentDescription = "Retour")
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

            profile == null -> Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                EmptyState(
                    title = "Profil indisponible",
                    message = state.error ?: "Impossible de charger ce profil pour l'instant.",
                )
            }

            else -> FriendProfileContent(
                profile = profile,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

@Composable
private fun FriendProfileContent(
    profile: FriendProfileDto,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            ProfileHeader(profile)
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            StatsRow(profile)
        }
        if (profile.commonSpecies.isNotEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                CommonSpeciesSection(profile.commonSpecies)
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
            Text(
                text = if (profile.sharedFlowers.isEmpty()) {
                    "Fleurs partagées avec moi"
                } else {
                    "Fleurs partagées avec moi (${profile.sharedFlowers.size})"
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (profile.sharedFlowers.isEmpty()) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "${profile.displayName} n'a encore rien partagé avec vous.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(profile.sharedFlowers, key = { it.id }) { flower ->
                FlowerThumbnail(flower)
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: FriendProfileDto) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (profile.avatarUrl != null) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = "Photo de profil",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = initialsOf(profile.displayName),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = profile.displayName.ifBlank { "—" },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "Amis depuis ${formatMonthYear(profile.friendsSince)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Bandeau de statistiques visibles : amis en commun, fleurs visibles, espèces communes. */
@Composable
private fun StatsRow(profile: FriendProfileDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard("👥", profile.mutualFriendsCount, "amis en commun", Modifier.weight(1f))
        StatCard("🌸", profile.visibleFlowerCount, "fleurs visibles", Modifier.weight(1f))
        StatCard("🌿", profile.commonSpecies.size, "espèces communes", Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(emoji: String, value: Int, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
            Text(text = value.toString(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CommonSpeciesSection(species: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Espèces communes",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            species.forEach { name ->
                AssistChip(onClick = {}, label = { Text("🌿 $name") })
            }
        }
    }
}

@Composable
private fun FlowerThumbnail(flower: FlowerDto) {
    AsyncImage(
        model = flower.previewPhotoUrls().firstOrNull(),
        contentDescription = flower.species ?: "Fleur partagée",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
    )
}

/** Initiales (1 à 2 lettres) pour l'avatar par défaut. */
private fun initialsOf(displayName: String): String {
    val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

/** Formate une date ISO8601 en « mois année » français (ex. « mai 2026 »). */
private fun formatMonthYear(iso: String): String {
    val parsed = runCatching { OffsetDateTime.parse(iso) }.getOrNull()
        ?: return "récemment"
    return parsed.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH))
}
