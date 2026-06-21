package com.florapin.app.map

import android.graphics.RectF
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.BuildConfig
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/** Style MapTiler (tuiles OSM) ; nécessite une clé API. */
private fun mapTilerStyleUrl(apiKey: String): String =
    "https://api.maptiler.com/maps/streets/style.json?key=$apiKey"

/** Caméra par défaut : vue large centrée sur la France métropolitaine. */
private val DEFAULT_CAMERA: CameraPosition = CameraPosition.Builder()
    .target(LatLng(46.6, 2.5))
    .zoom(4.0)
    .build()

/**
 * Carte MapLibre des fleurs (NODE-12 + NODE-13) : tuiles MapTiler/OSM, un
 * marqueur par fleur géolocalisée, regroupés en clusters quand ils sont proches.
 * Tap sur un cluster = zoom ; tap sur un marqueur = ouverture du détail.
 *
 * Sans clé MapTiler configurée, affiche un message d'aide.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onFlowerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(),
) {
    val apiKey = BuildConfig.MAPTILER_API_KEY
    val markers by viewModel.markers.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dateFilter.collectAsStateWithLifecycle()
    val currentOnFlowerClick by rememberUpdatedState(onFlowerClick)

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
            return@Scaffold
        }

        val mapView = rememberMapViewWithLifecycle()
        val style = remember { mutableStateOf<Style?>(null) }

        LaunchedEffect(mapView) {
            mapView.getMapAsync { map ->
                map.cameraPosition = DEFAULT_CAMERA
                map.setStyle(mapTilerStyleUrl(apiKey)) { loadedStyle ->
                    loadedStyle.setupFlowerClustering()
                    style.value = loadedStyle
                }
                map.addOnMapClickListener { latLng ->
                    handleMapClick(map, latLng) { id -> currentOnFlowerClick(id) }
                }
            }
        }

        // Met à jour les marqueurs dès que les données ou le style changent.
        LaunchedEffect(markers, style.value) {
            style.value?.updateFlowerMarkers(markers)
        }

        Column(modifier = Modifier.padding(innerPadding)) {
            FilterBar(
                selected = dateFilter,
                onSelect = viewModel::setDateFilter,
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView },
            )
        }
    }
}

/**
 * Barre de filtres : chips de période (fonctionnels) + chips « Ami » / « Espèce »
 * désactivés tant que les données correspondantes n'existent pas (backend social
 * NODE-15, identification NODE-24).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    selected: DateFilter,
    onSelect: (DateFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DateFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
        // Dimensions prévues mais pas encore alimentées.
        FilterChip(selected = false, enabled = false, onClick = {}, label = { Text("Ami") })
        FilterChip(selected = false, enabled = false, onClick = {}, label = { Text("Espèce") })
    }
}

/**
 * Gère un tap : zoom sur un cluster, ou ouverture du détail sur un marqueur
 * individuel. Renvoie true si l'évènement a été consommé.
 */
private fun handleMapClick(
    map: MapLibreMap,
    latLng: LatLng,
    onFlowerClick: (Long) -> Unit,
): Boolean {
    val screenPoint = map.projection.toScreenLocation(latLng)
    val touch = RectF(
        screenPoint.x - TOUCH_SLOP,
        screenPoint.y - TOUCH_SLOP,
        screenPoint.x + TOUCH_SLOP,
        screenPoint.y + TOUCH_SLOP,
    )

    if (map.queryRenderedFeatures(touch, MapLayers.CLUSTERS).isNotEmpty()) {
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(latLng, map.cameraPosition.zoom + 2),
        )
        return true
    }

    val point = map.queryRenderedFeatures(touch, MapLayers.UNCLUSTERED)
        .firstOrNull { it.hasProperty(MapLayers.PROP_ID) }
    if (point != null) {
        onFlowerClick(point.getNumberProperty(MapLayers.PROP_ID).toLong())
        return true
    }
    return false
}

private const val TOUCH_SLOP = 12f

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
