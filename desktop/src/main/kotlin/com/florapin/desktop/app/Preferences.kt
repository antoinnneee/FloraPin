package com.florapin.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.florapin.desktop.core.DesktopConfig
import java.io.File
import java.util.Properties

/** Apparence : suit Windows par défaut, forçable par l'utilisateur. */
enum class ThemeMode(val label: String) {
    SYSTEM("Automatique"),
    LIGHT("Clair"),
    DARK("Sombre"),
}

/**
 * Préférences d'interface, relues au démarrage suivant.
 *
 * Chaque champ est un état Compose : l'écriture met à jour l'UI immédiatement
 * et programme l'enregistrement. Le fichier est minuscule, on le réécrit en
 * entier à chaque changement plutôt que d'introduire un mécanisme différé.
 */
class Preferences(
    private val file: File = File(DesktopConfig.dataDir, "preferences.properties"),
) {
    private val props = Properties()

    init {
        if (file.isFile) runCatching { file.inputStream().use { props.load(it) } }
    }

    var themeMode: ThemeMode by persisted(
        key = "themeMode",
        initial = runCatching { ThemeMode.valueOf(props.getProperty("themeMode", "SYSTEM")) }
            .getOrDefault(ThemeMode.SYSTEM),
        encode = { it.name },
    )

    /** Côté d'une vignette en pixels ; réglable au clavier et à la molette. */
    var thumbnailSize: Int by persisted(
        key = "thumbnailSize",
        initial = props.getProperty("thumbnailSize")?.toIntOrNull()?.coerceIn(96, 420) ?: 180,
        encode = { it.toString() },
    )

    /** Identifiant de style MapTiler retenu pour la carte. */
    var mapStyleId: String by persisted(
        key = "mapStyleId",
        initial = props.getProperty("mapStyleId") ?: "bright-v2",
        encode = { it },
    )

    /** Dernier dossier d'export, proposé par défaut la fois suivante. */
    var lastExportDir: String by persisted(
        key = "lastExportDir",
        initial = props.getProperty("lastExportDir")
            ?: File(System.getProperty("user.home"), "Pictures").path,
        encode = { it },
    )

    /**
     * Dernière section consultée. Un compagnon de bureau reste ouvert des
     * journées entières et se relance souvent : rouvrir sur la carte quand on
     * y travaillait évite de refaire le chemin à chaque démarrage.
     */
    var lastSection: String by persisted(
        key = "lastSection",
        initial = props.getProperty("lastSection") ?: "LIBRARY",
        encode = { it },
    )

    /** Panneau de détail ouvert à droite de la photothèque. */
    var showDetailsPane: Boolean by persisted(
        key = "showDetailsPane",
        initial = props.getProperty("showDetailsPane")?.toBoolean() ?: true,
        encode = { it.toString() },
    )

    private fun <T> persisted(key: String, initial: T, encode: (T) -> String) =
        object : kotlin.properties.ReadWriteProperty<Any?, T> {
            private var state by mutableStateOf(initial)

            override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state

            override fun setValue(
                thisRef: Any?,
                property: kotlin.reflect.KProperty<*>,
                value: T,
            ) {
                if (state == value) return
                state = value
                props.setProperty(key, encode(value))
                save()
            }
        }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "FloraPin — préférences d'affichage") }
        }
    }
}
