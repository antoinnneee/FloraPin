package com.florapin.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.florapin.desktop.app.AppModel
import com.florapin.desktop.app.ThemeMode
import com.florapin.desktop.export.PhotoExporter
import com.florapin.desktop.map.MapCamera
import com.florapin.desktop.ui.FloraPinDesktopTheme
import javax.swing.UIManager

/**
 * Point d'entrée du compagnon Windows.
 *
 * Les boîtes de dialogue système (sélecteur de dossier) passent par Swing :
 * on aligne son apparence sur celle de Windows avant toute création de
 * fenêtre, sinon elles s'affichent avec le thème Java par défaut, qui jure
 * avec le reste de l'application.
 */
fun main() {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    application {
        val scope = rememberCoroutineScope()
        val model = remember { AppModel(scope) }
        val exporter = remember { PhotoExporter(scope) }
        val camera = remember { MapCamera() }

        LaunchedEffect(Unit) { model.restoreSession() }

        val darkTheme = when (model.preferences.themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "FloraPin — Compagnon",
            state = rememberWindowState(size = DpSize(1360.dp, 860.dp)),
            // Les raccourcis sont traités ici, après le composant qui a le
            // focus : un champ de saisie garde donc Ctrl+A et Suppr pour lui.
            onKeyEvent = ::handleGlobalShortcut,
        ) {
            window.minimumSize = java.awt.Dimension(960, 640)
            FloraPinDesktopTheme(darkTheme = darkTheme) {
                Surface { FloraPinApp(model, exporter, camera) }
            }
        }
    }
}
