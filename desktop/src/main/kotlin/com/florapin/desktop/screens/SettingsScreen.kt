package com.florapin.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.florapin.desktop.app.AppModel
import com.florapin.desktop.app.ThemeMode
import com.florapin.desktop.core.DesktopConfig
import com.florapin.desktop.core.ImageStore
import java.awt.Desktop

/** Réglages, informations de compte et entretien du cache local. */
@Composable
fun SettingsScreen(model: AppModel) {
    var cacheBytes by remember { mutableStateOf(ImageStore.diskCacheBytes()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineLarge)

        SettingsCard("Compte") {
            model.user?.let { user ->
                Row(Modifier.fillMaxWidth()) {
                    Text("Connecté en tant que", Modifier.width(180.dp), style = MaterialTheme.typography.bodyMedium)
                    Column {
                        Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.padding(4.dp))
            OutlinedButton(onClick = { model.signOut() }) { Text("Se déconnecter") }
            Text(
                text = "La modification du compte (mot de passe, adresse, suppression) " +
                    "se fait depuis l'application mobile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard("Apparence") {
            SingleChoiceSegmentedButtonRow {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = model.preferences.themeMode == mode,
                        onClick = { model.preferences.themeMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
        }

        SettingsCard("Carte") {
            Row(Modifier.fillMaxWidth()) {
                Text("Clé MapTiler", Modifier.width(180.dp), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (DesktopConfig.maptilerApiKey.isBlank()) {
                        "Non configurée — le fond de carte est indisponible"
                    } else {
                        "Configurée (${DesktopConfig.maptilerApiKey.take(4)}…)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = "Pour utiliser votre propre clé, ajoutez la ligne " +
                    "« maptilerApiKey=VOTRE_CLE » dans le fichier de configuration, " +
                    "puis relancez l'application.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openInExplorer(DesktopConfig.dataDir.path) }) {
                    Text("Ouvrir le dossier de configuration")
                }
            }
            Text(
                text = DesktopConfig.overrideFile.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        SettingsCard("Stockage local") {
            Row(Modifier.fillMaxWidth()) {
                Text("Cache des images", Modifier.width(180.dp), style = MaterialTheme.typography.bodyMedium)
                Text(formatBytes(cacheBytes), style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "Les photos consultées sont conservées sur le disque pour un " +
                    "affichage instantané et une récupération sans nouveau " +
                    "téléchargement. Les vider ne supprime rien de votre compte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = {
                val freed = ImageStore.clearDiskCache()
                cacheBytes = ImageStore.diskCacheBytes()
                model.status = "${formatBytes(freed)} libérés"
            }) { Text("Vider le cache") }
        }

        SettingsCard("À propos") {
            Row(Modifier.fillMaxWidth()) {
                Text("Serveur", Modifier.width(180.dp), style = MaterialTheme.typography.bodyMedium)
                Text(DesktopConfig.apiBaseUrl, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = "Compagnon Windows de FloraPin. La prise de photo reste sur " +
                    "l'application mobile ; l'import de photos existantes est prévu " +
                    "pour une prochaine version.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("Raccourcis clavier", style = MaterialTheme.typography.labelLarge)
            SHORTCUTS.forEach { (keys, description) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = keys,
                        modifier = Modifier.width(180.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

private val SHORTCUTS = listOf(
    "Ctrl+1 … Ctrl+6" to "Changer de section",
    "Ctrl+A" to "Tout sélectionner",
    "Ctrl+F" to "Rechercher",
    "Ctrl+E" to "Récupérer la sélection",
    "Ctrl+ +/-" to "Agrandir ou réduire les vignettes",
    "F5" to "Actualiser",
    "Suppr" to "Supprimer la sélection (confirmation)",
    "Échap" to "Fermer la visionneuse ou vider la sélection",
    "← →" to "Photo précédente / suivante dans la visionneuse",
    "Double-clic" to "Ouvrir en grand",
    "Clic droit" to "Menu contextuel",
)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f Go".format(bytes.toDouble() / (1L shl 30))
    bytes >= 1L shl 20 -> "%.0f Mo".format(bytes.toDouble() / (1L shl 20))
    else -> "%.0f Ko".format(bytes.toDouble() / 1024)
}

/** Ouvre un dossier dans l'Explorateur ; sans effet si l'API n'est pas dispo. */
private fun openInExplorer(path: String) {
    runCatching {
        if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(java.io.File(path))
    }
}
