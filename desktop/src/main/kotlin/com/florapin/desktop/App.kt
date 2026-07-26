package com.florapin.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import com.florapin.desktop.app.AppModel
import com.florapin.desktop.app.Section
import com.florapin.desktop.export.PhotoExporter
import com.florapin.desktop.map.MapCamera
import com.florapin.desktop.screens.AlbumsScreen
import com.florapin.desktop.screens.IdentifyScreen
import com.florapin.desktop.screens.LibraryScreen
import com.florapin.desktop.screens.LoginScreen
import com.florapin.desktop.screens.MapScreen
import com.florapin.desktop.screens.SearchFocusRequester
import com.florapin.desktop.screens.SettingsScreen
import com.florapin.desktop.ui.AlbumPickerDialog
import com.florapin.desktop.ui.ExportDialog
import com.florapin.desktop.ui.NavRail
import com.florapin.desktop.ui.StatusBar
import com.florapin.desktop.ui.UiActions
import com.florapin.desktop.ui.ViewerOverlay

/**
 * Coquille de l'application : navigation, actions transverses et fenêtres
 * modales. Les écrans eux-mêmes ne connaissent que le modèle et [UiActions].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FloraPinApp(model: AppModel, exporter: PhotoExporter, camera: MapCamera) {
    if (model.restoringSession) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (model.user == null) {
        LoginScreen(model)
        return
    }

    var contextMenuOpen by remember { mutableStateOf(false) }
    var albumPickerOpen by remember { mutableStateOf(false) }
    var exportOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Dernière position connue du curseur dans la zone de contenu : c'est
    // elle qui ancre le menu contextuel, Material 3 ne sachant pas se placer
    // seul sous le pointeur.
    var pointerInContent by remember { mutableStateOf(Offset.Zero) }

    val actions = remember(model, exporter) {
        UiActions(
            openViewer = { model.viewerFlowerId = it },
            openContextMenu = { contextMenuOpen = true },
            exportSelection = { exportOpen = true },
            addSelectionToAlbum = { albumPickerOpen = true },
            deleteSelection = { confirmDelete = true },
            requestIdentification = {
                model.requestIdentification(model.selection.ids.toList())
            },
            showOnMap = { id ->
                model.flowerById(id)?.let { flower ->
                    if (flower.latitude != null && flower.longitude != null) {
                        camera.moveTo(flower.latitude!!, flower.longitude!!, zoom = 15f)
                        camera.autoFramed = true
                        model.selection.selectOnly(id)
                        model.section = Section.MAP
                    } else {
                        model.status = "Cette photo n'a pas de position enregistrée."
                    }
                }
            },
        )
    }
    // Rendues accessibles à la fenêtre, qui route les raccourcis clavier sans
    // connaître l'écran affiché.
    currentActions = actions
    currentModel = model

    Row(Modifier.fillMaxSize()) {
        NavRail(
            current = model.section,
            identifyBadge = model.toIdentify.count { it.needsIdentification },
            onSelect = {
                model.section = it
                model.selection.clear()
            },
        )
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .weight(1f)
                    .onPointerEvent(PointerEventType.Move, PointerEventPass.Initial) { event ->
                        event.changes.firstOrNull()?.let { pointerInContent = it.position }
                    },
            ) {
                when (model.section) {
                    Section.LIBRARY, Section.SHARED -> LibraryScreen(model, actions)
                    Section.ALBUMS -> AlbumsScreen(model, actions)
                    Section.MAP -> MapScreen(model, actions, camera)
                    Section.IDENTIFY -> IdentifyScreen(model)
                    Section.SETTINGS -> SettingsScreen(model)
                }

                if (contextMenuOpen) {
                    SelectionContextMenu(
                        model = model,
                        actions = actions,
                        at = pointerInContent,
                        onDismiss = { contextMenuOpen = false },
                    )
                }
            }
            StatusBar(
                itemCount = model.visibleFlowers.size,
                selectedCount = model.selection.size,
                loading = model.loading,
                message = model.status,
                onDismissMessage = { model.status = null },
            )
        }
    }

    model.viewerFlowerId?.let { id ->
        ViewerOverlay(
            model = model,
            flowerId = id,
            onClose = { model.viewerFlowerId = null },
            onNavigate = { model.viewerFlowerId = it },
        )
    }

    if (albumPickerOpen) {
        AlbumPickerDialog(
            model = model,
            onDismiss = { albumPickerOpen = false },
        )
    }

    if (exportOpen) {
        ExportDialog(
            model = model,
            exporter = exporter,
            onDismiss = { exportOpen = false },
        )
    }

    if (confirmDelete) {
        val count = model.selection.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer $count photo(s) ?") },
            text = {
                Text(
                    "La suppression est définitive et s'applique aussi à l'application " +
                        "mobile. Pensez à récupérer vos photos avant, si vous voulez les garder.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.deleteFlowers(model.selection.ids.toList())
                        confirmDelete = false
                    },
                ) { Text("Supprimer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annuler") }
            },
        )
    }
}

/** Menu contextuel de la sélection, ouvert au clic droit sous le curseur. */
@Composable
private fun SelectionContextMenu(
    model: AppModel,
    actions: UiActions,
    at: Offset,
    onDismiss: () -> Unit,
) {
    val ids = model.selection.ids
    val single = ids.singleOrNull()?.let(model::flowerById)
    val allMine = ids.mapNotNull(model::flowerById).all { model.isMine(it) }

    val density = LocalDensity.current
    val offset = with(density) { DpOffset(at.x.toDp(), at.y.toDp()) }

    DropdownMenu(expanded = true, onDismissRequest = onDismiss, offset = offset) {
        if (single != null) {
            MenuItem("Ouvrir", Icons.Outlined.OpenInFull) {
                actions.openViewer(single.id)
                onDismiss()
            }
        }
        MenuItem("Récupérer (Ctrl+E)", Icons.Outlined.SaveAlt) {
            actions.exportSelection()
            onDismiss()
        }
        MenuItem("Ajouter à un album…", Icons.Outlined.LibraryAdd) {
            actions.addSelectionToAlbum()
            onDismiss()
        }
        if (single?.latitude != null) {
            MenuItem("Voir sur la carte", Icons.Outlined.Place) {
                actions.showOnMap(single.id)
                onDismiss()
            }
        }
        if (allMine) {
            HorizontalDivider()
            MenuItem("Demander l'identification", Icons.Outlined.HelpOutline) {
                actions.requestIdentification()
                onDismiss()
            }
            MenuItem("Supprimer…", Icons.Outlined.Delete) {
                actions.deleteSelection()
                onDismiss()
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}

// La fenêtre reçoit les raccourcis avant toute composition ; ces références lui
// donnent accès à l'état courant sans le faire transiter par Main.
private var currentActions: UiActions? = null
private var currentModel: AppModel? = null

/**
 * Raccourcis clavier de l'application.
 *
 * Aucun raccourci n'est immédiatement destructeur : `Suppr` ouvre une
 * confirmation, comme partout sous Windows où la suppression passe par la
 * corbeille — ici, le serveur n'en a pas.
 *
 * Renvoie `true` si l'événement est consommé.
 */
fun handleGlobalShortcut(event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val model = currentModel ?: return false
    val actions = currentActions ?: return false
    if (model.user == null) return false

    // La visionneuse capte les flèches et Échap : elle est modale.
    if (model.viewerFlowerId != null) {
        return when (event.key) {
            Key.Escape -> {
                model.viewerFlowerId = null
                true
            }
            else -> false
        }
    }

    if (event.isCtrlPressed) {
        val sections = Section.entries
        val digit = DIGIT_KEYS.indexOf(event.key)
        if (digit in sections.indices) {
            model.section = sections[digit]
            model.selection.clear()
            return true
        }
        return when (event.key) {
            Key.A -> {
                model.selection.selectAll(model.visibleFlowers.map { it.id })
                true
            }
            Key.F -> {
                runCatching { SearchFocusRequester.requestFocus() }
                true
            }
            Key.E -> {
                if (!model.selection.isEmpty) actions.exportSelection()
                true
            }
            Key.Equals, Key.Plus -> {
                model.preferences.thumbnailSize =
                    (model.preferences.thumbnailSize + 30).coerceAtMost(380)
                true
            }
            Key.Minus -> {
                model.preferences.thumbnailSize =
                    (model.preferences.thumbnailSize - 30).coerceAtLeast(110)
                true
            }
            else -> false
        }
    }

    return when (event.key) {
        Key.F5 -> {
            model.refreshAll()
            true
        }
        Key.Escape -> {
            if (!model.selection.isEmpty) {
                model.selection.clear()
                true
            } else {
                false
            }
        }
        Key.Delete -> {
            val mine = model.selection.ids.mapNotNull(model::flowerById).all { model.isMine(it) }
            if (!model.selection.isEmpty && mine) {
                actions.deleteSelection()
                true
            } else {
                false
            }
        }
        else -> false
    }
}

private val DIGIT_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)
