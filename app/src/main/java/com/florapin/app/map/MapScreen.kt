package com.florapin.app.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.florapin.app.BuildConfig
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

/** Style MapTiler (tuiles OSM) ; nécessite une clé API. */
private fun mapTilerStyleUrl(apiKey: String): String =
    "https://api.maptiler.com/maps/streets/style.json?key=$apiKey"

/** Caméra par défaut : vue large centrée sur la France métropolitaine. */
private val DEFAULT_CAMERA: CameraPosition = CameraPosition.Builder()
    .target(LatLng(46.6, 2.5))
    .zoom(4.0)
    .build()

/**
 * Carte MapLibre (NODE-12) : intégration de base avec les tuiles MapTiler/OSM.
 * Les marqueurs des fleurs et les filtres viendront avec NODE-13/NODE-14.
 *
 * Sans clé MapTiler configurée, affiche un message d'aide plutôt qu'une carte
 * vide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val apiKey = BuildConfig.MAPTILER_API_KEY

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Carte") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { innerPadding ->
        if (apiKey.isBlank()) {
            MissingKeyMessage(modifier = Modifier.padding(innerPadding))
        } else {
            val mapView = rememberMapViewWithLifecycle()
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { mapView },
                update = { view ->
                    view.getMapAsync { map ->
                        map.cameraPosition = DEFAULT_CAMERA
                        map.setStyle(mapTilerStyleUrl(apiKey))
                    }
                },
            )
        }
    }
}

@Composable
private fun MissingKeyMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Clé MapTiler manquante.\n\n" +
                "Ajoutez MAPTILER_API_KEY=<votre_clé> dans local.properties " +
                "(ou en variable d'environnement) puis relancez le build.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}
