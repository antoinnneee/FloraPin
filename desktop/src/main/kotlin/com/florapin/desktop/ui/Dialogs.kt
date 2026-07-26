package com.florapin.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.florapin.desktop.app.AppModel
import com.florapin.desktop.export.ExportFormat
import com.florapin.desktop.export.ExportLayout
import com.florapin.desktop.export.ExportOptions
import com.florapin.desktop.export.PhotoExporter
import java.io.File
import javax.swing.JFileChooser

/**
 * Ajout de la sélection à un album, avec création à la volée : c'est le geste
 * de classement le plus fréquent, il ne doit pas obliger à passer par l'écran
 * Albums puis à revenir.
 */
@Composable
fun AlbumPickerDialog(model: AppModel, onDismiss: () -> Unit) {
    var newAlbumName by remember { mutableStateOf("") }
    val count = model.selection.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter $count photo(s) à un album") },
        text = {
            Column {
                if (model.albums.isEmpty()) {
                    Text(
                        text = "Aucun album pour l'instant. Créez le premier ci-dessous.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(model.albums, key = { it.id }) { album ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .mouseInteractions(
                                        onClick = {
                                            model.addToAlbum(
                                                album.id,
                                                model.selection.ids.toList(),
                                            )
                                            onDismiss()
                                        },
                                    )
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Folder, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(album.name, Modifier.weight(1f))
                                Text(
                                    text = "${album.flowerIds.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newAlbumName,
                        onValueChange = { newAlbumName = it },
                        label = { Text("Nouvel album") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            model.createAlbum(newAlbumName)
                            // L'album vient d'être créé côté serveur ; les
                            // photos y seront ajoutées au rafraîchissement de
                            // la liste, l'utilisateur reste sur sa sélection.
                            newAlbumName = ""
                            onDismiss()
                        },
                        enabled = newAlbumName.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/**
 * Récupération des photos sur le disque.
 *
 * L'écran expose délibérément l'arborescence produite et le format : un
 * utilisateur qui « récupère ses photos » veut un dossier exploitable ensuite,
 * pas un tas de fichiers aux noms opaques.
 */
@Composable
fun ExportDialog(model: AppModel, exporter: PhotoExporter, onDismiss: () -> Unit) {
    var destination by remember {
        mutableStateOf(File(model.preferences.lastExportDir))
    }
    var layout by remember { mutableStateOf(ExportLayout.BY_DATE) }
    var format by remember { mutableStateOf(ExportFormat.ORIGINAL) }
    var allPhotos by remember { mutableStateOf(true) }
    var writeCatalog by remember { mutableStateOf(true) }

    val selected = model.selection.ids
    val flowers = remember(selected, model.myFlowers, model.sharedFlowers) {
        selected.mapNotNull(model::flowerById)
    }

    AlertDialog(
        onDismissRequest = { if (!exporter.running) onDismiss() },
        title = { Text(if (exporter.running) "Récupération en cours…" else "Récupérer mes photos") },
        text = {
            Column(Modifier.width(520.dp).verticalScroll(rememberScrollState())) {
                if (exporter.running) {
                    LinearProgressIndicator(
                        progress = { exporter.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${exporter.done} / ${exporter.total} — ${exporter.currentLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                    return@Column
                }

                exporter.report?.let {
                    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Text(
                    text = "${flowers.size} fiche(s) sélectionnée(s).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(14.dp))

                Text("Dossier de destination", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = destination.path,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        chooseDirectory(destination)?.let { destination = it }
                    }) { Text("Parcourir…") }
                }

                Spacer(Modifier.height(14.dp))
                Text("Organisation", style = MaterialTheme.typography.labelLarge)
                ExportLayout.entries.forEach { option ->
                    OptionRow(
                        selected = layout == option,
                        title = option.label,
                        subtitle = option.description,
                        onSelect = { layout = option },
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("Format", style = MaterialTheme.typography.labelLarge)
                ExportFormat.entries.forEach { option ->
                    OptionRow(
                        selected = format == option,
                        title = option.label,
                        subtitle = option.description,
                        onSelect = { format = option },
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.mouseInteractions(onClick = { allPhotos = !allPhotos }),
                ) {
                    Checkbox(allPhotos, onCheckedChange = { allPhotos = it })
                    Text("Toutes les photos de chaque fiche, pas seulement la couverture")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.mouseInteractions(onClick = { writeCatalog = !writeCatalog }),
                ) {
                    Checkbox(writeCatalog, onCheckedChange = { writeCatalog = it })
                    Text("Joindre un récapitulatif (espèces, dates, positions)")
                }
            }
        },
        confirmButton = {
            if (exporter.running) {
                TextButton(onClick = { exporter.cancel() }) { Text("Interrompre") }
            } else {
                Button(
                    onClick = {
                        model.preferences.lastExportDir = destination.path
                        exporter.start(
                            flowers = flowers,
                            albums = model.albums,
                            options = ExportOptions(
                                destination = destination,
                                layout = layout,
                                format = format,
                                allPhotos = allPhotos,
                                writeCatalog = writeCatalog,
                            ),
                        )
                    },
                    enabled = flowers.isNotEmpty(),
                ) { Text("Récupérer") }
            }
        },
        dismissButton = {
            if (!exporter.running) TextButton(onClick = onDismiss) { Text("Fermer") }
        },
    )
}

@Composable
private fun OptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Sélecteur de dossier natif. `JFileChooser` s'exécute sur le thread d'IHM,
 * qui est aussi celui de Compose Desktop : l'appel direct est correct.
 */
private fun chooseDirectory(initial: File): File? {
    val chooser = JFileChooser(if (initial.isDirectory) initial else null).apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Choisir le dossier où récupérer les photos"
        isMultiSelectionEnabled = false
    }
    return if (chooser.showDialog(null, "Choisir ce dossier") == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile
    } else {
        null
    }
}

/** Zone vide réutilisée par les dialogues sans contenu. */
@Composable
fun DialogPlaceholder(text: String) {
    Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
