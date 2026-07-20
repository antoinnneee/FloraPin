package com.florapin.app.map

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.RectF
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.shadow
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
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

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
    val friendsOnly by viewModel.friendsOnly.collectAsStateWithLifecycle()
    val currentOnFlowerClick by rememberUpdatedState(onFlowerClick)

    // Photo d'une fleur d'ami ouverte en plein écran (pas de détail local à ouvrir).
    var friendFlower by remember { mutableStateOf<FlowerMarker?>(null) }
    val currentOnFriendPhotoClick by rememberUpdatedState<(Long) -> Unit> { markerId ->
        friendFlower = markers.firstOrNull { !it.navigable && it.id == markerId }
    }

    friendFlower?.let { marker ->
        FullscreenPhotoViewer(
            models = marker.photoUrls.ifEmpty { listOfNotNull(marker.photoUrl) },
            startIndex = 0,
            onDismiss = { friendFlower = null },
            detailsContent = { FriendFlowerDetails(marker) },
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
            // Le style de carte vit directement en overlay sur la carte.
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
                val density = context.resources.displayMetrics.density
                map.uiSettings.setCompassMargins(
                    0,
                    (72 * density).toInt(),
                    (12 * density).toInt(),
                    0,
                )
                map.addOnMapClickListener { latLng ->
                    handleMapClick(
                        map = map,
                        style = style.value,
                        latLng = latLng,
                        onFlowerClick = { id -> currentOnFlowerClick(id) },
                        onFriendPhotoClick = { id -> currentOnFriendPhotoClick(id) },
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

        // Active le point « ma position » dès que le style est prêt et la
        // permission accordée (à réappliquer après chaque rechargement de style).
        LaunchedEffect(style.value, locationGranted) {
            val loadedStyle = style.value
            val map = mapRef.value
            if (loadedStyle != null && map != null && locationGranted) {
                map.enableMyLocation(context, loadedStyle)
            }
        }

        // Appels photo déjà enregistrés dans le style courant. Un rechargement
        // de style (changement de fond de carte) repart sans image : la clé
        // `remember` les fait repartir de zéro avec lui.
        val photoIconIds = remember(style.value) { mutableStateOf(emptySet<Long>()) }
        val calloutMotionState = remember(style.value) { CalloutMotionState() }
        val currentMarkers by rememberUpdatedState(markers)
        val currentPhotoIconIds by rememberUpdatedState(photoIconIds.value)

        // Recalcule aussi pendant le geste : une passe courte suit le drag sans
        // bloquer l'UI, puis une relaxation complète affine le placement à l'arrêt.
        DisposableEffect(mapRef.value, style.value, mapView) {
            val map = mapRef.value
            val loadedStyle = style.value
            if (map == null || loadedStyle == null) return@DisposableEffect onDispose {}
            var lastLiveLayoutAt = 0L
            fun refreshCallouts(relaxationSteps: Int, interpolation: Float) {
                loadedStyle.updateFlowerCallouts(
                    map = map,
                    markers = currentMarkers,
                    photoIconIds = currentPhotoIconIds,
                    viewportWidth = mapView.width.toFloat(),
                    viewportHeight = mapView.height.toFloat(),
                    relaxationSteps = relaxationSteps,
                    motionState = calloutMotionState,
                    interpolation = interpolation,
                )
            }
            val settleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = CALLOUT_SETTLE_DURATION_MS
                addUpdateListener {
                    refreshCallouts(
                        relaxationSteps = CALLOUT_RELAXATION_STEPS,
                        interpolation = SETTLE_INTERPOLATION,
                    )
                }
                addListener(object : AnimatorListenerAdapter() {
                    private var cancelled = false

                    override fun onAnimationStart(animation: Animator) {
                        cancelled = false
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        cancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!cancelled) {
                            refreshCallouts(CALLOUT_RELAXATION_STEPS, 1f)
                        }
                    }
                })
            }
            val moveListener = MapLibreMap.OnCameraMoveListener {
                if (settleAnimator.isRunning) settleAnimator.cancel()
                val now = SystemClock.uptimeMillis()
                if (now - lastLiveLayoutAt >= LIVE_LAYOUT_INTERVAL_MS) {
                    lastLiveLayoutAt = now
                    refreshCallouts(
                        relaxationSteps = CALLOUT_LIVE_RELAXATION_STEPS,
                        interpolation = LIVE_INTERPOLATION,
                    )
                }
            }
            val idleListener = MapLibreMap.OnCameraIdleListener {
                if (settleAnimator.isRunning) settleAnimator.cancel()
                settleAnimator.start()
            }
            map.addOnCameraMoveListener(moveListener)
            map.addOnCameraIdleListener(idleListener)
            idleListener.onCameraIdle()
            onDispose {
                settleAnimator.cancel()
                map.removeOnCameraMoveListener(moveListener)
                map.removeOnCameraIdleListener(idleListener)
            }
        }

        // Met à jour les marqueurs dès que les données ou le style changent. Les
        // vignettes sont décodées après coup : la carte s'affiche tout de suite en
        // emojis, puis les bulles reliées apparaissent au fur et à mesure.
        LaunchedEffect(markers, style.value) {
            val loadedStyle = style.value ?: return@LaunchedEffect
            loadedStyle.updateFlowerMarkers(markers)

            val loaded = photoIconIds.value.toMutableSet()
            var added = false
            markers.take(MAX_PHOTO_ICONS).forEach { marker ->
                if (marker.id in loaded) return@forEach
                val bitmap = loadCircularIcon(
                    context = context,
                    model = marker.thumbnailModel,
                    borderColor = if (marker.navigable) {
                        android.graphics.Color.WHITE
                    } else {
                        FRIEND_PHOTO_BORDER_COLOR
                    },
                ) ?: return@forEach
                // Le style a pu être remplacé pendant le décodage : y ajouter une
                // image lèverait une exception.
                if (style.value !== loadedStyle) return@LaunchedEffect
                loadedStyle.addImage(
                    photoIconId(marker.id, friend = !marker.navigable),
                    bitmap,
                )
                loaded += marker.id
                added = true
            }
            if (added) {
                photoIconIds.value = loaded
            }
            mapRef.value?.let { map ->
                loadedStyle.updateFlowerCallouts(
                    map = map,
                    markers = markers,
                    photoIconIds = loaded,
                    viewportWidth = mapView.width.toFloat(),
                    viewportHeight = mapView.height.toFloat(),
                    motionState = calloutMotionState,
                    interpolation = LIVE_INTERPOLATION,
                )
            }
        }

        Column(modifier = Modifier.padding(innerPadding)) {
            FilterBar(
                selected = dateFilter,
                onSelect = viewModel::setDateFilter,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { mapView },
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverlayFriendChip(
                        selected = friendsOnly,
                        onClick = viewModel::toggleFriendsOnly,
                    )
                    MapStyleChip(
                        selected = selectedStyle,
                        onSelect = {
                            selectedStyle = it
                            stylePrefs.set(it)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Sélecteur compact de période. Les contrôles cartographiques vivent en overlay.
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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.height(36.dp)) {
            DateFilter.entries.forEachIndexed { index, filter ->
                SegmentedButton(
                    modifier = Modifier.height(36.dp),
                    shape = SegmentedButtonDefaults.itemShape(index, DateFilter.entries.size),
                    selected = filter == selected,
                    onClick = { onSelect(filter) },
                    icon = {},
                    label = {
                        Text(
                            text = when (filter) {
                                DateFilter.ALL -> "Toutes"
                                DateFilter.LAST_7_DAYS -> "7 j"
                                DateFilter.LAST_30_DAYS -> "30 j"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                )
            }
        }
    }
}

/** Chip de choix du style, superposé dans le coin supérieur droit de la carte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapStyleChip(
    selected: MapStyle,
    onSelect: (MapStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)

    Box(modifier = modifier) {
        FilterChip(
            modifier = Modifier
                .shadow(6.dp, shape)
                .background(MaterialTheme.colorScheme.surface, shape),
            selected = false,
            onClick = { expanded = true },
            leadingIcon = { DecorativeEmoji("🗺️") },
            label = { Text(selected.label) },
            colors = FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            border = null,
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

/** Affichage des fleurs d'amis, activé au démarrage. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayFriendChip(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    FilterChip(
        modifier = Modifier
            .shadow(6.dp, shape)
            .background(MaterialTheme.colorScheme.surface, shape),
        selected = selected,
        onClick = onClick,
        leadingIcon = { DecorativeEmoji("👥") },
        label = { Text("Amis") },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
        border = null,
    )
}

/**
 * Gère un tap : zoom sur un cluster, ouverture du détail sur une de mes fleurs,
 * ou affichage de la photo pour une fleur d'ami (pas de détail local). Renvoie
 * true si l'évènement a été consommé.
 */
private fun handleMapClick(
    map: MapLibreMap,
    style: Style?,
    latLng: LatLng,
    onFlowerClick: (Long) -> Unit,
    onFriendPhotoClick: (Long) -> Unit,
): Boolean {
    val screenPoint = map.projection.toScreenLocation(latLng)
    val touch = RectF(
        screenPoint.x - TOUCH_SLOP,
        screenPoint.y - TOUCH_SLOP,
        screenPoint.x + TOUCH_SLOP,
        screenPoint.y + TOUCH_SLOP,
    )

    val cluster = map.queryRenderedFeatures(touch, MapLayers.CLUSTERS).firstOrNull()
    if (cluster != null) {
        val source = style?.getSourceAs<GeoJsonSource>(MapLayers.SOURCE)
        val expansionZoom = runCatching {
            source?.getClusterExpansionZoom(cluster)?.toDouble()
        }.getOrNull()
        val clusterPoint = cluster.geometry() as? Point
        val target = clusterPoint?.let { LatLng(it.latitude(), it.longitude()) } ?: latLng
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                target,
                expansionZoom ?: (map.cameraPosition.zoom + 2),
            ),
        )
        return true
    }

    val features = map.queryRenderedFeatures(
        touch,
        MapLayers.CALLOUTS,
        MapLayers.UNCLUSTERED,
    )

    // Mes fleurs d'abord : leur détail complet prime sur la photo seule d'un ami
    // dont le marqueur se superposerait.
    val mine = features.firstOrNull { it.hasProperty(MapLayers.PROP_ID) }
    if (mine != null) {
        onFlowerClick(mine.getNumberProperty(MapLayers.PROP_ID).toLong())
        return true
    }

    val friend = features.firstOrNull {
        it.hasProperty(MapLayers.PROP_PHOTO) && it.hasProperty(MapLayers.PROP_MARKER_ID)
    }
    if (friend != null) {
        onFriendPhotoClick(friend.getNumberProperty(MapLayers.PROP_MARKER_ID).toLong())
        return true
    }
    return false
}

@Composable
private fun FriendFlowerDetails(marker: FlowerMarker) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 96.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = marker.species?.takeIf { it.isNotBlank() } ?: "Espece non identifiee",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DetailField(
            label = "Commentaire",
            value = marker.notes?.takeIf { it.isNotBlank() } ?: "Aucun commentaire",
        )
        formatFriendDate(marker.takenAt)?.let { date ->
            DetailField(label = "Date", value = date)
        }
        if (marker.tags.isNotEmpty()) {
            DetailField(label = "Tags", value = marker.tags.joinToString("  "))
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatFriendDate(value: String?): String? {
    val parts = value?.take(10)?.split("-") ?: return null
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else value
}

private const val TOUCH_SLOP = 12f
private const val LIVE_LAYOUT_INTERVAL_MS = 32L
private const val CALLOUT_SETTLE_DURATION_MS = 240L
private const val LIVE_INTERPOLATION = 0.24f
private const val SETTLE_INTERPOLATION = 0.22f

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
