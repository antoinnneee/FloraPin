package com.florapin.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.florapin.app.capture.CaptureFlow
import com.florapin.app.gallery.GalleryScreen

/** Destinations de l'application. */
private object Routes {
    const val GALLERY = "gallery"
    const val CAPTURE = "capture"
}

/**
 * Graphe de navigation principal : galerie (écran d'accueil) ↔ capture.
 * Le détail d'une fleur sera ajouté ultérieurement (NODE-10).
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
                onFlowerClick = { /* détail : NODE-10 */ },
            )
        }
        composable(Routes.CAPTURE) {
            CaptureFlow(
                onFinished = { navController.popBackStack() },
            )
        }
    }
}
