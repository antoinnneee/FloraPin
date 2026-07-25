package com.florapin.app.ui.layout

import android.content.res.Configuration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Vrai lorsque l'appareil est en paysage. En paysage la hauteur utile est rare :
 * les barres système de l'app se compactent pour rendre l'espace au contenu.
 */
@Composable
@ReadOnlyComposable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

/** Hauteur de la bottom bar, hors encoche système. */
val bottomBarHeight: Dp
    @Composable get() = if (isLandscape()) 52.dp else 64.dp

/** Hauteur du berceau photo qui dépasse au-dessus de la base de navigation. */
val bottomBarBumpHeight: Dp
    @Composable get() = if (isLandscape()) 22.dp else 32.dp

/**
 * Zone réellement masquée par la navigation flottante.
 *
 * Les écrans racine restent dessinés bord à bord derrière la barre. Leurs
 * conteneurs défilables utilisent cette valeur comme espace de fin afin que le
 * dernier élément puisse remonter entièrement au-dessus du berceau. L'inset de
 * navigation système est déjà réservé par le Scaffold propre à chaque écran.
 */
val bottomBarScrollClearance: Dp
    @Composable get() = bottomBarHeight + bottomBarBumpHeight

/** Hauteur d'une [androidx.compose.material3.TopAppBar]. */
val topBarHeight: Dp
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable get() = if (isLandscape()) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight
