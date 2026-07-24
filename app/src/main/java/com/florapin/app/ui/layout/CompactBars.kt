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

/** Hauteur d'une [androidx.compose.material3.TopAppBar]. */
val topBarHeight: Dp
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable get() = if (isLandscape()) 48.dp else TopAppBarDefaults.TopAppBarExpandedHeight
