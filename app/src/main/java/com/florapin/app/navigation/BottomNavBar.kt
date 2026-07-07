package com.florapin.app.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.florapin.app.R

/**
 * Onglets principaux de la bottom navigation bar (NODE-110). Chaque onglet
 * correspond à une destination racine persistante : Accueil (galerie), Albums,
 * Carte, Partagées (feed) et Profil. Le reste de la navigation (Amis, Détail,
 * Capture) reste poussé par-dessus, sans onglet dédié.
 *
 * [icon] est une illustration botanique multicolore (marque FloraPin). On la
 * rend via [Image] et non [androidx.compose.material3.Icon] : Icon appliquerait
 * une teinte monochrome qui écraserait les couleurs. L'état actif reste porté
 * par l'indicateur de la NavigationBar et la couleur du libellé.
 */
enum class TopLevelDestination(
    val route: String,
    @DrawableRes val icon: Int,
    val label: String,
) {
    HOME("gallery", R.drawable.ic_nav_home, "Accueil"),
    ALBUMS("albums", R.drawable.ic_nav_albums, "Albums"),
    MAP("map", R.drawable.ic_nav_map, "Carte"),
    FEED("feed", R.drawable.ic_nav_feed, "Partagées"),
    PROFILE("profile", R.drawable.ic_nav_profile, "Profil"),
}

/** Routes des onglets racine, pour décider de l'affichage de la barre. */
val topLevelRoutes: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()

/**
 * Barre de navigation inférieure à 4 onglets. [currentRoute] détermine l'onglet
 * actif ; [onSelect] est invoqué avec la destination choisie. [feedBadge] est le
 * nombre de fleurs non vues du feed « Partagées » (0 = pas de badge).
 */
@Composable
fun FloraBottomBar(
    currentRoute: String?,
    onSelect: (TopLevelDestination) -> Unit,
    feedBadge: Int = 0,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val badge = if (destination == TopLevelDestination.FEED) feedBadge else 0
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = { onSelect(destination) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (badge > 0) {
                                Badge { Text(if (badge > 99) "99+" else "$badge") }
                            }
                        },
                    ) {
                        // L'illustration est décorative : le libellé de l'onglet
                        // (« Accueil », « Albums »…) porte déjà le sens pour TalkBack,
                        // d'où contentDescription = null.
                        Image(
                            painter = painterResource(destination.icon),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                },
                label = { Text(destination.label) },
            )
        }
    }
}
