package com.florapin.app.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.ui.components.BadgeCard
import com.florapin.app.ui.components.BadgeUiState

/**
 * Onglet ② Badges (TÂCHE 5.5) : grille des familles de badges avec rangées
 * d'étoiles qui se remplissent et progression « 34 / 50 ». Deux sections —
 * « Collection » (locale) et « Entraide » (serveur, grisée hors-ligne).
 *
 * Célébration au déblocage : un palier fraîchement obtenu déclenche un retour
 * haptique (cf. QOL 6.15) et affiche un liseré sur la carte concernée.
 */
@Composable
internal fun BadgesTab(
    viewModel: BadgesViewModel = viewModel(
        factory = BadgesViewModel.factory(LocalContext.current),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.celebrate) {
        if (state.celebrate) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.celebrationConsumed()
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fullWidthItem {
            Text(
                text = "🌿 ${state.starsUnlocked} / ${state.starsTotal} étoiles",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        section("Collection", state.collection)

        fullWidthItem {
            Text(
                text = "Entraide",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (!state.entraideAvailable) {
            fullWidthItem {
                Text(
                    text = "Compteurs indisponibles hors ligne : reconnectez-vous " +
                        "pour voir votre entraide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.entraide, key = { it.id }) { badge ->
            BadgeCard(state = badge)
        }
    }
}

/** Ajoute un titre de section en pleine largeur suivi de ses cartes. */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.section(
    title: String,
    badges: List<BadgeUiState>,
) {
    fullWidthItem {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
    items(badges, key = { it.id }) { badge ->
        BadgeCard(state = badge)
    }
}

/** Élément de grille occupant toute la largeur (en-tête / titre de section). */
private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullWidthItem(
    content: @Composable () -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth(),
        ) { content() }
    }
}
