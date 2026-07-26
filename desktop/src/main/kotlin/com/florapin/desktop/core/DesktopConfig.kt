package com.florapin.desktop.core

import java.io.File
import java.util.Properties

/**
 * Configuration du compagnon Windows.
 *
 * Trois niveaux, du plus faible au plus fort :
 *  1. `florapin.properties` embarqué, généré au build depuis `local.properties` ;
 *  2. `config.properties` dans le dossier de données, éditable par l'utilisateur
 *     pour viser une autre instance du backend sans reconstruire l'application ;
 *  3. variables d'environnement, pratiques pour un lancement ponctuel en
 *     terminal (`API_BASE_URL=... florapin.exe`).
 *
 * Le MSI est distribué sans clé MapTiler quand le poste de build n'en avait
 * pas : le niveau 2 permet alors à l'utilisateur d'ajouter la sienne.
 */
object DesktopConfig {

    /**
     * Dossier de données : jetons de session, cache d'images, préférences.
     * `%LOCALAPPDATA%` (et non `%APPDATA%`) car rien ici ne doit suivre un
     * profil itinérant — le cache d'images peut peser plusieurs centaines de Mo.
     */
    val dataDir: File = resolveDataDir()

    /** Cache disque des images téléchargées (miniatures et pleine résolution). */
    val imageCacheDir: File = File(dataDir, "cache/images")

    private val values: Properties = loadValues()

    /** URL de base de l'API REST, terminée par `/` (exigence Retrofit). */
    val apiBaseUrl: String = read("apiBaseUrl", "API_BASE_URL")
        .ifBlank { "https://florapin.pattounecorp.ovh/api/v1/" }
        .let { if (it.endsWith("/")) it else "$it/" }

    /** Clé MapTiler ; vide = la carte affiche une invite de configuration. */
    val maptilerApiKey: String = read("maptilerApiKey", "MAPTILER_API_KEY")

    /** Chemin du fichier de surcharge, affiché dans l'écran de réglages. */
    val overrideFile: File = File(dataDir, "config.properties")

    private fun read(key: String, envKey: String): String =
        (System.getenv(envKey) ?: values.getProperty(key) ?: "").trim()

    private fun loadValues(): Properties {
        val props = Properties()
        // Valeurs de build.
        DesktopConfig::class.java.getResourceAsStream("/florapin.properties")
            ?.use { props.load(it) }
        // Surcharge utilisateur : ne remplace que les clés réellement fournies.
        val override = File(dataDir, "config.properties")
        if (override.isFile) {
            val user = Properties()
            runCatching { override.inputStream().use { user.load(it) } }
            user.stringPropertyNames()
                .filter { user.getProperty(it).isNotBlank() }
                .forEach { props.setProperty(it, user.getProperty(it)) }
        }
        return props
    }

    private fun resolveDataDir(): File {
        val base = System.getenv("LOCALAPPDATA")
            ?: System.getProperty("user.home")
            ?: "."
        return File(base, "FloraPin").apply { mkdirs() }
    }
}
