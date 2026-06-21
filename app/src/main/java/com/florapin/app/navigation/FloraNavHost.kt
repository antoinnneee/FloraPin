package com.florapin.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.florapin.app.capture.CaptureFlow
import com.florapin.app.detail.DetailScreen
import com.florapin.app.gallery.GalleryScreen
import com.florapin.app.map.MapScreen

/** Destinations de l'application. */
private object Routes {
    const val GALLERY = "gallery"
    const val CAPTURE = "capture"
    const val MAP = "map"
    const val DETAIL = "detail/{id}"

    fun detail(id: Long) = "detail/$id"
}

/**
 * Graphe de navigation principal : galerie (accueil) ↔ capture, et détail d'une
 * fleur.
 */
@Composable
fun FloraNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.GALLERY,
        modifier = modifier,
    ) {
        composable(Routes.GALLERY) {
            GalleryScreen(
                onCapture = { navController.navigate(Routes.CAPTURE) },
                onFlowerClick = { id -> navController.navigate(Routes.detail(id)) },
                onOpenMap = { navController.navigate(Routes.MAP) },
            )
        }
        composable(Routes.CAPTURE) {
            CaptureFlow(
                onFinished = { navController.popBackStack() },
            )
        }
        composable(Routes.MAP) {
            MapScreen(
                onBack = { navController.popBackStack() },
                onFlowerClick = { id -> navController.navigate(Routes.detail(id)) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: return@composable
            DetailScreen(
                flowerId = id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
