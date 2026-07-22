package com.florapin.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.florapin.app.ui.theme.BadgeCompletedBanner
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

    /** Une action est déjà comptée, même si la première étoile reste à atteindre. */
    val started: Boolean get() = available && currentValue > 0

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
    val shape = RoundedCornerShape(20.dp)
    val (containerColor, contentColor) = when {
        // Un badge terminé adopte le vert de feuillage : c'est l'état final et
        // positif de la collection.
        state.maxed -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer

        // Une progression en cours reste dans la gamme des feuillages, avec un
        // vert plus doux que celui du badge entièrement terminé.
        state.started -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer

        // Pas encore de palier, ou compteur indisponible : état neutre.
        else -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    val outlineColor = when {
        state.isNew -> BadgeNewHighlight
        state.maxed -> BadgeCompletedBanner.copy(alpha = 0.75f)
        state.started -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant
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
                .border(if (state.isNew) 2.dp else 1.dp, outlineColor, shape)
                .semantics {
                    contentDescription = "${describe(state)}. ${state.description}"
                },
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (state.maxed) 5.dp else 0.dp,
            ),
            shape = shape,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (state.maxed) {
                    CompletedBotanicalBackdrop(
                        color = contentColor,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 170.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (state.maxed) {
                        CompletedBanner(singleTier = state.singleTier)
                    } else {
                        BadgeStatusLabel(
                            label = when {
                                !state.available -> "HORS LIGNE"
                                state.started -> "EN PROGRESSION"
                                else -> "À DÉCOUVRIR"
                            },
                            active = state.started,
                            modifier = Modifier.padding(top = 11.dp),
                        )
                    }
                    Text(
                        text = state.emoji,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .alpha(if (state.started || state.maxed) 1f else 0.32f),
                    )
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                    StarRow(total = state.tiers.size, filled = state.unlockedTiers)
                    if (state.started && !state.maxed && !state.singleTier) {
                        TierProgress(
                            state = state,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    } else {
                        Text(
                            text = progressionLabel(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.82f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 5.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedBanner(singleTier: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BadgeCompletedBanner)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (singleTier) "✦ DÉBLOQUÉ" else "✦ COMPLÉTÉ",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF352500),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BadgeStatusLabel(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (active) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        },
        modifier = modifier,
    )
}

@Composable
private fun TierProgress(
    state: BadgeUiState,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(
            progress = { tierProgress(state) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
            color = color,
            trackColor = color.copy(alpha = 0.16f),
            strokeCap = StrokeCap.Round,
        )
        Text(
            text = progressionLabel(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun tierProgress(state: BadgeUiState): Float {
    val next = state.nextTier ?: return 1f
    val previous = state.tiers.lastOrNull { it <= state.currentValue } ?: 0
    val range = (next - previous).coerceAtLeast(1)
    return ((state.currentValue - previous).toFloat() / range).coerceIn(0f, 1f)
}

/** Rameau en filigrane : la collection a atteint sa pleine maturité. */
@Composable
private fun CompletedBotanicalBackdrop(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val botanical = color.copy(alpha = 0.10f)
        val gold = BadgeCompletedBanner.copy(alpha = 0.22f)
        drawLine(
            color = botanical,
            start = Offset(size.width * 0.68f, size.height * 0.18f),
            end = Offset(size.width * 0.98f, size.height * 0.86f),
            strokeWidth = 2.dp.toPx(),
        )
        drawOval(
            color = botanical,
            topLeft = Offset(size.width * 0.70f, size.height * 0.30f),
            size = Size(size.width * 0.20f, size.height * 0.10f),
        )
        drawOval(
            color = botanical,
            topLeft = Offset(size.width * 0.79f, size.height * 0.52f),
            size = Size(size.width * 0.19f, size.height * 0.11f),
        )
        drawCircle(
            color = gold,
            radius = 5.dp.toPx(),
            center = Offset(size.width * 0.18f, size.height * 0.70f),
        )
        drawCircle(
            color = gold,
            radius = 2.dp.toPx(),
            center = Offset(size.width * 0.10f, size.height * 0.82f),
        )
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
