package com.florapin.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.florapin.desktop.app.Section

/** Icône associée à chaque section du rail. */
private fun Section.icon(): ImageVector = when (this) {
    Section.LIBRARY -> Icons.Filled.PhotoLibrary
    Section.ALBUMS -> Icons.Filled.Collections
    Section.MAP -> Icons.Filled.Map
    Section.IDENTIFY -> Icons.Filled.Spa
    Section.SHARED -> Icons.Filled.People
    Section.SETTINGS -> Icons.Filled.Settings
}

/**
 * Rail de navigation permanent.
 *
 * L'app mobile utilise une barre inférieure : sur un écran large, elle
 * gaspillerait la hauteur — la ressource rare sur un moniteur 16:9 — alors que
 * la largeur abonde. Le rail affiche en plus le raccourci clavier de chaque
 * section, pour que l'utilisateur régulier cesse vite d'utiliser la souris.
 */
@Composable
fun NavRail(
    current: Section,
    identifyBadge: Int,
    onSelect: (Section) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxHeight().width(96.dp),
    ) {
        Column(
            Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Section.entries.forEachIndexed { index, section ->
                if (section == Section.SETTINGS) Spacer(Modifier.weight(1f))
                RailItem(
                    section = section,
                    selected = section == current,
                    shortcut = "Ctrl+${index + 1}",
                    badge = if (section == Section.IDENTIFY) identifyBadge else 0,
                    onClick = { onSelect(section) },
                )
            }
        }
    }
}

@Composable
private fun RailItem(
    section: Section,
    selected: Boolean,
    shortcut: String,
    badge: Int,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        hovered -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val tint = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .hoverable(interactionSource)
            .mouseInteractions(onClick = { onClick() })
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BadgedBox(badge = { if (badge > 0) Badge { Text(badge.toString()) } }) {
            Icon(section.icon(), contentDescription = section.label, tint = tint)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = section.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        if (hovered && !selected) {
            Text(
                text = shortcut,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * Barre d'état : ce que l'on regarde, ce qui est sélectionné, et le dernier
 * message. Elle remplace les notifications éphémères du mobile — sur un poste
 * de travail, une confirmation qui disparaît au bout de deux secondes se
 * manque, surtout pendant un export.
 */
@Composable
fun StatusBar(
    itemCount: Int,
    selectedCount: Int,
    loading: Boolean,
    message: String?,
    onDismissMessage: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth()) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = buildString {
                        append("$itemCount élément")
                        if (itemCount > 1) append("s")
                        if (selectedCount > 0) append("  ·  $selectedCount sélectionné")
                        if (selectedCount > 1) append("s")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f)) {
                    message?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.mouseInteractions(onClick = { onDismissMessage() }),
                        ) {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Message centré d'un écran vide, avec une piste d'action. */
@Composable
fun EmptyState(title: String, hint: String, icon: ImageVector = Icons.Filled.PhotoLibrary) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(32.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(56.dp),
        )
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
