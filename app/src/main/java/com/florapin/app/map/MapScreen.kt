package com.florapin.app.map

import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.florapin.app.BuildConfig
import com.florapin.app.ui.components.DecorativeEmoji
import com.florapin.app.ui.components.EmojiIcon
import com.florapin.app.ui.components.FullscreenPhotoViewer
import com.florapin.app.ui.layout.topBarHeight
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

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
    onFlowerClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = viewModel(),
) {
    val apiKey = BuildConfig.MAPTILER_API_KEY
    val markers by viewModel.markers.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dateFilter.collectAsStateWithLifecycle()
    val speciesFilter by viewModel.speciesFilter.collectAsStateWithLifecycle()
    val friendsOnly by viewModel.friendsOnly.collectAsStateWithLifecycle()
    val availableSpecies by viewModel.availableSpecies.collectAsStateWithLifecycle()
    val currentOnFlowerClick by rememberUpdatedState(onFlowerClick)

    // Photo d'une fleur d'ami ouverte en plein écran (pas de détail local à ouvrir).
    var friendPhoto by remember { mutableStateOf<String?>(null) }
    val currentOnFriendPhotoClick by rememberUpdatedState<(String) -> Unit> {
        friendPhoto = it
    }

    friendPhoto?.let { url ->
        FullscreenPhotoViewer(
            models = listOf(url),
            startIndex = 0,
            onDismiss = { friendPhoto = null },
        )
    }

    val context = LocalContext.current
    val stylePrefs = remember { MapStylePreferences(context) }
    var selectedStyle by remember { mutableStateOf(stylePrefs.get()) }

    // Position de l'utilisateur : permission + référence carte partagées entre le
    // bouton de recentrage (FAB) et le contenu.
    val mapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    var locationGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { locationGranted = hasLocationPermission(context) }

    // À l'ouverture de la carte, demande la localisation si pas encore accordée.
    LaunchedEffect(Unit) {
        if (apiKey.isNotBlank() && !locationGranted) {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            // Topbar épurée : le style de carte est descendu dans la barre de
            // filtres (chip « 🗺️ »), comme le reste des contrôles.
            TopAppBar(
                expandedHeight = topBarHeight,
                title = { Text("Carte") },
            )
        },
        floatingActionButton = {
            if (apiKey.isNotBlank()) {
                FloatingActionButton(
                    onClick = {
                        if (locationGranted) {
                            mapRef.value?.centerOnMyLocation()
                        } else {
                            permissionLauncher.launch(LOCATION_PERMISSIONS)
                        }
                    },
                ) {
                    EmojiIcon("📍", contentDescription = "Recentrer sur ma position")
                }
            }
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
                mapRef.value = map
                map.cameraPosition = DEFAULT_CAMERA
                map.addOnMapClickListener { latLng ->
                    handleMapClick(
                        map = map,
                        latLng = latLng,
                        onFlowerClick = { id -> currentOnFlowerClick(id) },
                        onFriendPhotoClick = { url -> currentOnFriendPhotoClick(url) },
                    )
                }
            }
        }

        // (Re)charge le style à l'ouverture et à chaque changement de sélection.
        LaunchedEffect(mapRef.value, selectedStyle) {
            mapRef.value?.setStyle(mapTilerStyleUrl(apiKey, selectedStyle)) { loadedStyle ->
                loadedStyle.setupFlowerClustering(context)
                style.value = loadedStyle
            }
        }

        // Les pastilles grossissent avec le zoom, sans se chevaucher : leur
        // échelle dépend de l'écart, à l'écran, des deux fleurs les plus proches.
        // Un rechargement de style repart de l'échelle inscrite dans la couche,
        // d'où ce suivi hors de `style` (cf. réapplication plus bas).
        val currentMarkers by rememberUpdatedState(markers)
        val appliedScale = remember { mutableStateOf(PHOTO_ICON_SCALE_INITIAL) }
        DisposableEffect(mapRef.value) {
            val map = mapRef.value ?: return@DisposableEffect onDispose {}
            val listener = MapLibreMap.OnCameraMoveListener {
                val loadedStyle = style.value ?: return@OnCameraMoveListener
                if (map.cameraPosition.zoom < PHOTO_ICON_MIN_ZOOM) {
                    return@OnCameraMoveListener
                }
                val scale = map.photoIconScaleForCamera(currentMarkers)
                if (isScaleChangeVisible(appliedScale.value, scale)) {
                    appliedScale.value = scale
                    loadedStyle.setPhotoIconScale(scale)
                }
            }
            map.addOnCameraMoveListener(listener)
            onDispose { map.removeOnCameraMoveListener(listener) }
        }

        // Active le point « ma position » dès que le style est prêt et la
        // permission accordée (à réappliquer après chaque rechargement de style).
        LaunchedEffect(style.value, locationGranted) {
            val loadedStyle = style.value
            val map = mapRef.value
            if (loadedStyle != null && map != null && locationGranted) {
                map.enableMyLocation(context, loadedStyle)
            }
        }

        // Pastilles photo déjà enregistrées dans le style courant. Un rechargement
        // de style (changement de fond de carte) repart sans image : la clé
        // `remember` les fait repartir de zéro avec lui.
        val photoIconIds = remember(style.value) { mutableStateOf(emptySet<Long>()) }

        // Met à jour les marqueurs dès que les données ou le style changent. Les
        // vignettes sont décodées après coup : la carte s'affiche tout de suite en
        // emojis, puis les pastilles apparaissent au fur et à mesure.
        LaunchedEffect(markers, style.value) {
            val loadedStyle = style.value ?: return@LaunchedEffect
            loadedStyle.updateFlowerMarkers(markers, photoIconIds.value)

            // Le style neuf porte l'échelle initiale, et un lot de marqueurs peut
            // resserrer le voisinage sans que la caméra ait bougé.
            mapRef.value?.let { map ->
                appliedScale.value = map.photoIconScaleForCamera(markers)
                loadedStyle.setPhotoIconScale(appliedScale.value)
            }

            val loaded = photoIconIds.value.toMutableSet()
            var added = false
            markers.take(MAX_PHOTO_ICONS).forEach { marker ->
                if (marker.id in loaded) return@forEach
                val bitmap = loadCircularIcon(context, marker.thumbnailModel)
                    ?: return@forEach
                // Le style a pu être remplacé pendant le décodage : y ajouter une
                // image lèverait une exception.
                if (style.value !== loadedStyle) return@LaunchedEffect
                loadedStyle.addImage(photoIconId(marker.id), bitmap)
                loaded += marker.id
                added = true
            }
            if (added) {
                photoIconIds.value = loaded
                loadedStyle.updateFlowerMarkers(markers, loaded)
            }
        }

        Column(modifier = Modifier.padding(innerPadding)) {
            FilterBar(
                selected = dateFilter,
                onSelect = viewModel::setDateFilter,
                species = availableSpecies,
                selectedSpecies = speciesFilter,
                onSelectSpecies = viewModel::setSpeciesFilter,
                friendsOnly = friendsOnly,
                onToggleFriends = viewModel::toggleFriendsOnly,
                style = selectedStyle,
                onSelectStyle = {
                    selectedStyle = it
                    stylePrefs.set(it)
                },
            )
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView },
            )
        }
    }
}

/**
 * Barre de filtres : période, appartenance (« Ami ») et espèce. Le chip espèce
 * ouvre un menu déroulant alimenté par les espèces présentes ; il est désactivé
 * tant qu'aucune fleur ne porte d'espèce.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    selected: DateFilter,
    onSelect: (DateFilter) -> Unit,
    species: List<String>,
    selectedSpecies: String?,
    onSelectSpecies: (String?) -> Unit,
    friendsOnly: Boolean,
    onToggleFriends: () -> Unit,
    style: MapStyle,
    onSelectStyle: (MapStyle) -> Unit,
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

        FilterChip(
            selected = friendsOnly,
            onClick = onToggleFriends,
            label = { Text("Ami") },
        )

        SpeciesFilterChip(
            species = species,
            selectedSpecies = selectedSpecies,
            onSelectSpecies = onSelectSpecies,
        )

        // Apparence de la carte (distincte des filtres de données) : chip affichant
        // le style courant en toutes lettres, avec menu de sélection.
        MapStyleChip(selected = style, onSelect = onSelectStyle)
    }
}

/** Chip de choix du style (apparence) de la carte, dans la barre de filtres. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapStyleChip(
    selected: MapStyle,
    onSelect: (MapStyle) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            leadingIcon = { DecorativeEmoji("🗺️") },
            label = { Text(selected.label) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = {
                        val mark = if (style == selected) "✓ " else ""
                        Text("$mark${style.label}")
                    },
                    onClick = {
                        onSelect(style)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** Chip « Espèce » avec menu déroulant des espèces disponibles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesFilterChip(
    species: List<String>,
    selectedSpecies: String?,
    onSelectSpecies: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = selectedSpecies != null,
            enabled = species.isNotEmpty(),
            onClick = { expanded = true },
            label = { Text(selectedSpecies ?: "Espèce") },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Toutes") },
                onClick = {
                    onSelectSpecies(null)
                    expanded = false
                },
            )
            species.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelectSpecies(name)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Gère un tap : zoom sur un cluster, ouverture du détail sur une de mes fleurs,
 * ou affichage de la photo pour une fleur d'ami (pas de détail local). Renvoie
 * true si l'évènement a été consommé.
 */
private fun handleMapClick(
    map: MapLibreMap,
    latLng: LatLng,
    onFlowerClick: (Long) -> Unit,
    onFriendPhotoClick: (String) -> Unit,
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

    val features = map.queryRenderedFeatures(touch, MapLayers.UNCLUSTERED)

    // Mes fleurs d'abord : leur détail complet prime sur la photo seule d'un ami
    // dont le marqueur se superposerait.
    val mine = features.firstOrNull { it.hasProperty(MapLayers.PROP_ID) }
    if (mine != null) {
        onFlowerClick(mine.getNumberProperty(MapLayers.PROP_ID).toLong())
        return true
    }

    val friend = features.firstOrNull { it.hasProperty(MapLayers.PROP_PHOTO) }
    if (friend != null) {
        onFriendPhotoClick(friend.getStringProperty(MapLayers.PROP_PHOTO))
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
