package com.florapin.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.florapin.app.ui.theme.BadgeNewHighlight
import com.florapin.app.ui.theme.BadgeStarEmpty
import com.florapin.app.ui.theme.BadgeStarFilled

/**
 * État visuel d'une carte de badge (TÂCHE 5.5), produit par le ViewModel de
 * l'onglet Badges à partir du catalogue ([com.florapin.app.badges.BadgeCatalog])
 * et de la valeur courante (progression locale ou compteur serveur).
 *
 * @param currentValue numérateur brut affiché (« 34 » dans « 34 / 50 »).
 * @param available `false` quand la donnée est indisponible (badge serveur
 *   hors-ligne, ou badge géo sans résolveur de régions) : la carte est alors
 *   entièrement grisée et sans progression chiffrée (device-first).
 * @param isNew palier fraîchement débloqué : liseré de célébration.
 */
data class BadgeUiState(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String = title,
    val tiers: List<Int>,
    val currentValue: Int,
    val available: Boolean = true,
    val isNew: Boolean = false,
) {
    /** Nombre d'étoiles remplies (paliers atteints). 0 si la donnée est indisponible. */
    val unlockedTiers: Int
        get() = if (!available) 0 else tiers.count { currentValue >= it }

    /** Au moins un palier atteint : la carte est « allumée ». */
    val unlocked: Boolean get() = unlockedTiers > 0

    /** Tous les paliers atteints. */
    val maxed: Boolean get() = available && unlockedTiers == tiers.size

    /** Prochain seuil à franchir, ou `null` si tous atteints. */
    val nextTier: Int? get() = tiers.firstOrNull { currentValue < it }

    /** Famille à palier unique (première fleur) : pas de progression chiffrée. */
    val singleTier: Boolean get() = tiers.size == 1
}

/**
 * Carte de badge partagée de la grille (TÂCHE 5.5). Rend l'emoji de la famille,
 * son nom, une **rangée d'étoiles** (une par palier atteignable, remplies au fur
 * et à mesure) et la progression « 34 / 50 » vers le prochain palier.
 *
 * États de la DA :
 *  - **Aucun palier** → carte grisée (emoji atténué, étoiles creuses).
 *  - **Palier(s) atteint(s)** → étoiles or remplies + progression.
 *  - **Complété** → toutes les étoiles remplies, mention « Complété ».
 *  - **Indisponible** (serveur hors-ligne / géo sans résolveur) → grisé, « Hors ligne ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeCard(
    state: BadgeUiState,
    modifier: Modifier = Modifier,
) {
    val highlight = if (state.isNew) {
        Modifier.border(2.dp, BadgeNewHighlight, CardDefaults.shape)
    } else {
        Modifier
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltipText(state))
            }
        },
        state = rememberTooltipState(),
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .then(highlight)
                .semantics {
                    contentDescription = "${describe(state)}. ${state.description}"
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 128.dp)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = state.emoji,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.alpha(if (state.unlocked) 1f else 0.35f),
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (state.unlocked) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                StarRow(total = state.tiers.size, filled = state.unlockedTiers)
                Text(
                    text = progressionLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun tooltipText(state: BadgeUiState): String {
    val thresholds = if (state.singleTier) {
        "Palier : ${state.tiers.first()}"
    } else {
        "Paliers : ${state.tiers.joinToString()}"
    }
    return "${state.description}\n$thresholds"
}

/** Rangée d'étoiles : [filled] pleines (or) puis le reste creuses (gris). */
@Composable
private fun StarRow(total: Int, filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(total) { index ->
            val on = index < filled
            Text(
                text = if (on) "★" else "☆",
                style = MaterialTheme.typography.bodyMedium,
                color = if (on) BadgeStarFilled else BadgeStarEmpty,
            )
        }
    }
}

/** Texte de progression sous les étoiles (« 34 / 50 », « Complété », « Hors ligne »…). */
private fun progressionLabel(state: BadgeUiState): String = when {
    !state.available -> "Hors ligne"
    state.maxed && state.singleTier -> "Débloqué"
    state.maxed -> "Complété"
    state.singleTier -> "À débloquer"
    else -> "${state.currentValue} / ${state.nextTier}"
}

/** Description accessibilité concise de l'état de la carte. */
private fun describe(state: BadgeUiState): String = when {
    !state.available -> "${state.title} : indisponible hors ligne"
    state.singleTier && state.unlocked -> "${state.title} : débloqué"
    state.singleTier -> "${state.title} : non débloqué"
    else -> "${state.title} : ${state.unlockedTiers} étoiles sur ${state.tiers.size}, " +
        "progression ${state.currentValue}"
}
