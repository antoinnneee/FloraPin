package com.florapin.app.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Réglages de partage par défaut, partagés entre l'onboarding (première ouverture)
 * et Profil › Configuration : ils préremplissent la feuille de partage.
 *
 * Chaque changement est persisté immédiatement dans [SharePreferences] : il n'y a
 * pas de bouton « Enregistrer », l'écran d'onboarding n'ayant qu'un « Continuer ».
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharingDefaultsForm(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember(context) { SharePreferences(context) }

    var includeGps by remember { mutableStateOf(prefs.includeGps()) }
    var recipient by remember { mutableStateOf(prefs.defaultRecipient()) }
    var autoShare by remember { mutableStateOf(prefs.autoShare()) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Destinataire par défaut", style = MaterialTheme.typography.bodyMedium)
        // FlowRow : à l'étroit, c'est le second chip qui descend d'une ligne —
        // jamais son libellé qui se coupe en deux.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = recipient == DefaultRecipient.NONE,
                onClick = {
                    recipient = DefaultRecipient.NONE
                    prefs.setDefaultRecipient(DefaultRecipient.NONE)
                    // Sans destinataire, le partage automatique n'a plus de cible.
                    autoShare = false
                    prefs.setAutoShare(false)
                },
                label = { ChipLabel("Choisir à chaque fois") },
            )
            FilterChip(
                selected = recipient == DefaultRecipient.ALL_FRIENDS,
                onClick = {
                    recipient = DefaultRecipient.ALL_FRIENDS
                    prefs.setDefaultRecipient(DefaultRecipient.ALL_FRIENDS)
                },
                label = { ChipLabel("👥 Tous mes amis") },
            )
        }

        CheckboxRow(
            checked = includeGps,
            label = "Inclure la position GPS",
            onCheckedChange = {
                includeGps = it
                prefs.setIncludeGps(it)
            },
        )

        CheckboxRow(
            checked = autoShare,
            enabled = recipient != DefaultRecipient.NONE,
            label = "Partager automatiquement mes nouvelles fleurs",
            onCheckedChange = {
                autoShare = it
                prefs.setAutoShare(it)
            },
        )

        Text(
            text = if (recipient == DefaultRecipient.NONE) {
                "Le partage automatique demande un destinataire par défaut."
            } else {
                "Ces valeurs sont proposées à l'ouverture de la feuille de partage ; " +
                    "vous pouvez toujours les changer photo par photo."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Libellé de chip tenant sur une seule ligne (les deux chips sont côte à côte). */
@Composable
private fun ChipLabel(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun CheckboxRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
