package com.florapin.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Onglets principaux de la bottom navigation bar (NODE-110). Chaque onglet
 * correspond à une destination racine persistante : Accueil (galerie), Albums,
 * Carte, Partagées (feed) et Profil. Le reste de la navigation (Amis, Détail,
 * Capture) reste poussé par-dessus, sans onglet dédié.
 */
enum class TopLevelDestination(
    val route: String,
    val emoji: String,
    val label: String,
) {
    HOME("gallery", "🏠", "Accueil"),
    ALBUMS("albums", "📁", "Albums"),
    MAP("map", "🗺️", "Carte"),
    FEED("feed", "🖼️", "Partagées"),
    PROFILE("profile", "👤", "Profil"),
}

/** Routes des onglets racine, pour décider de l'affichage de la barre. */
val topLevelRoutes: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()

/**
 * Barre de navigation inférieure à 4 onglets. [currentRoute] détermine l'onglet
 * actif ; [onSelect] est invoqué avec la destination choisie.
 */
@Composable
fun FloraBottomBar(
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination) },
                icon = {
                    Text(destination.emoji, style = MaterialTheme.typography.titleLarge)
                },
                label = { Text(destination.label) },
            )
        }
    }
}
