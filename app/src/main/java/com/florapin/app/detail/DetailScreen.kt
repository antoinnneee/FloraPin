package com.florapin.app.detail

import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.florapin.app.BuildConfig
import com.florapin.app.albums.AddToAlbumSheet
import com.florapin.app.capture.CameraScreen
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.PhotoEntity
import com.florapin.app.data.imageModel
import com.florapin.app.data.thumbnailModel
import com.florapin.app.identify.IdentificationRequestSection
import com.florapin.app.identify.IdentificationRequestViewModel
import com.florapin.app.likes.LikeButton
import com.florapin.app.likes.LikeViewModel
import com.florapin.app.location.GeoPoint
import com.florapin.app.map.FlowerEmoji
import com.florapin.app.map.FlowerMarker
import com.florapin.app.map.mapTilerStyleUrl
import com.florapin.app.map.rememberMapViewWithLifecycle
import com.florapin.app.map.setupFlowerClustering
import com.florapin.app.map.updateFlowerMarkers
import com.florapin.app.network.dto.SpeciesDto
import com.florapin.app.share.ShareFlowerSheet
import com.florapin.app.util.formatCaptureDate
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

/**
 * Détail d'une fleur (NODE-10) : photo, coordonnées, mini-carte, notes éditables
 * et suppression.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    flowerId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSpecies: (String) -> Unit = {},
    viewModel: DetailViewModel = viewModel(),
    photosViewModel: PhotosViewModel = viewModel(),
    speciesPicker: SpeciesPickerViewModel = viewModel(
        factory = SpeciesPickerViewModel.factory(LocalContext.current),
    ),
    identificationVm: IdentificationRequestViewModel = viewModel(
        factory = IdentificationRequestViewModel.factory(LocalContext.current),
    ),
    likeVm: LikeViewModel = viewModel(
        factory = LikeViewModel.factory(LocalContext.current),
    ),
) {
    viewModel.setFlowerId(flowerId)
    photosViewModel.setFlowerId(flowerId)
    val flower by viewModel.flower.collectAsStateWithLifecycle()
    val photos by photosViewModel.photos.collectAsStateWithLifecycle()
    val likeState by likeVm.state.collectAsStateWithLifecycle()
    val serverId = flower?.serverId
    androidx.compose.runtime.LaunchedEffect(serverId) {
        serverId?.let(likeVm::bind)
    }
    var showShare by remember { mutableStateOf(false) }
    var showAddToAlbum by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }

    if (showCamera) {
        CameraScreen(
            onPhotoSaved = { uri ->
                uri.path?.let(photosViewModel::addPhoto)
                showCamera = false
            },
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Détail") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    val current = flower
                    if (current != null) {
                        IconButton(onClick = { showAddToAlbum = true }) { Text("📁") }
                        IconButton(onClick = { showShare = true }) { Text("📤") }
                    }
                    IconButton(
                        onClick = { viewModel.delete(onDeleted = onBack) },
                    ) { Text("🗑️") }
                },
            )
        },
    ) { innerPadding ->
        val current = flower
        if (current == null) {
            // Soit en cours de chargement, soit supprimée (après quoi onBack a lieu).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Chargement…")
            }
        } else {
            DetailContent(
                flower = current,
                photos = photos,
                speciesPicker = speciesPicker,
                identificationVm = identificationVm,
                onSaveNotes = viewModel::saveNotes,
                onSaveClassification = viewModel::saveClassification,
                onSetFeedPublication = viewModel::setFeedPublication,
                likeState = likeState,
                onToggleLike = likeVm::toggle,
                onOpenSpecies = onOpenSpecies,
                onAddPhoto = { showCamera = true },
                onDeletePhoto = photosViewModel::deletePhoto,
                onMakeCover = photosViewModel::makeCover,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (showShare && flower != null) {
        ShareFlowerSheet(
            flowerServerId = flower?.serverId,
            onDismiss = { showShare = false },
        )
    }

    if (showAddToAlbum && flower != null) {
        AddToAlbumSheet(
            flowerLocalId = flowerId,
            onDismiss = { showAddToAlbum = false },
        )
    }
}

@Composable
private fun DetailContent(
    flower: FlowerEntity,
    photos: List<PhotoEntity>,
    speciesPicker: SpeciesPickerViewModel,
    identificationVm: IdentificationRequestViewModel,
    onSaveNotes: (String) -> Unit,
    onSaveClassification: (String, List<String>, SpeciesDto?) -> Unit,
    onSetFeedPublication: (Boolean, Boolean) -> Unit,
    likeState: com.florapin.app.likes.LikeState,
    onToggleLike: () -> Unit,
    onOpenSpecies: (String) -> Unit,
    onAddPhoto: () -> Unit,
    onDeletePhoto: (PhotoEntity) -> Unit,
    onMakeCover: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = flower.imageModel(),
            contentDescription = "Photo de la fleur",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PhotoGallery(
                photos = photos,
                onAddPhoto = onAddPhoto,
                onDeletePhoto = onDeletePhoto,
                onMakeCover = onMakeCover,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatCaptureDate(flower.createdAt),
                    style = MaterialTheme.typography.titleMedium,
                )
                // Cœur disponible une fois la fleur synchronisée (NODE-140).
                if (flower.serverId != null && likeState.loaded) {
                    LikeButton(
                        liked = likeState.liked,
                        count = likeState.count,
                        onToggle = onToggleLike,
                    )
                }
            }

            val point = flower.toGeoPoint()
            if (point != null) {
                Text(
                    text = "📍 ${point.format()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                MiniMap(point, emoji = FlowerEmoji.forSpecies(flower.species))
            } else {
                Text(
                    text = "📍 Position non enregistrée",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            ClassificationEditor(
                flowerId = flower.id,
                initialSpecies = flower.species.orEmpty(),
                initialTags = flower.tags,
                speciesPicker = speciesPicker,
                onSave = onSaveClassification,
            )

            // Lien vers la fiche d'espèce quand la fleur est rattachée (NODE-151).
            flower.speciesId?.let { speciesId ->
                val label = flower.speciesCommonName
                    ?: flower.speciesScientificName
                    ?: flower.species
                    ?: "cette espèce"
                Text(
                    text = "🌿 Voir la fiche : $label",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSpecies(speciesId) }
                        .padding(vertical = 4.dp),
                )
            }

            // Publication au flux d'amis (NODE-137) : pour ses propres fleurs.
            FeedPublicationSection(
                flowerId = flower.id,
                published = flower.visibility == "friends",
                includeGps = flower.feedIncludeGps,
                onSetPublication = onSetFeedPublication,
            )

            // Demande d'identification aux amis quand l'espèce est absente (NODE-134).
            // Nécessite une fleur déjà synchronisée (serverId), car la demande est
            // adressée aux amis via l'API.
            if (flower.species.isNullOrBlank()) {
                IdentificationRequestSection(
                    flowerServerId = flower.serverId,
                    viewModel = identificationVm,
                )
            }

            NotesEditor(
                flowerId = flower.id,
                initialNotes = flower.notes,
                onSave = onSaveNotes,
            )
        }
    }
}

/**
 * Galerie des photos additionnelles d'une fleur (NODE-108) : carrousel
 * horizontal, ajout, suppression et choix de la couverture.
 */
@Composable
private fun PhotoGallery(
    photos: List<PhotoEntity>,
    onAddPhoto: () -> Unit,
    onDeletePhoto: (PhotoEntity) -> Unit,
    onMakeCover: (PhotoEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Photos (${photos.size + 1})",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onAddPhoto) { Text("➕ Ajouter une photo") }
        }
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { photo ->
                    PhotoThumbnail(
                        photo = photo,
                        onDelete = { onDeletePhoto(photo) },
                        onMakeCover = { onMakeCover(photo) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(
    photo: PhotoEntity,
    onDelete: () -> Unit,
    onMakeCover: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = photo.thumbnailModel(),
            contentDescription = "Photo supplémentaire",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Row {
            // « Définir comme couverture » seulement si la photo a un fichier local.
            if (photo.imagePath.isNotEmpty()) {
                IconButton(onClick = onMakeCover, modifier = Modifier.size(36.dp)) {
                    Text("⭐")
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Text("🗑️")
            }
        }
    }
}

/**
 * Édition de l'espèce (autocomplétée sur le référentiel, NODE-150) et des
 * étiquettes (saisies séparées par des virgules).
 *
 * Le champ espèce interroge /species/search ; sélectionner une suggestion fixe
 * le nom scientifique et rattache la fleur (species_id). Une saisie libre reste
 * possible : aucune fiche n'est alors associée.
 */
@Composable
private fun ClassificationEditor(
    flowerId: Long,
    initialSpecies: String,
    initialTags: List<String>,
    speciesPicker: SpeciesPickerViewModel,
    onSave: (String, List<String>, SpeciesDto?) -> Unit,
) {
    val initialTagsText = initialTags.joinToString(", ")
    var species by remember(flowerId) { mutableStateOf(initialSpecies) }
    var tagsText by remember(flowerId) { mutableStateOf(initialTagsText) }
    // Fiche du référentiel sélectionnée ; null tant que l'utilisateur tape librement.
    var selected by remember(flowerId) { mutableStateOf<SpeciesDto?>(null) }
    val suggestions by speciesPicker.suggestions.collectAsStateWithLifecycle()

    val changed = species != initialSpecies ||
        tagsText != initialTagsText ||
        selected != null

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = species,
            onValueChange = {
                species = it
                selected = null // toute frappe invalide la fiche choisie
                speciesPicker.onQueryChange(it)
            },
            label = { Text("Espèce") },
            singleLine = true,
            supportingText = {
                selected?.let { Text("Rattachée à « ${it.commonName} »") }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty() && selected == null) {
            SpeciesSuggestions(
                suggestions = suggestions,
                onPick = { picked ->
                    species = picked.scientificName
                    selected = picked
                    speciesPicker.clear()
                },
            )
        }
        OutlinedTextField(
            value = tagsText,
            onValueChange = { tagsText = it },
            label = { Text("Étiquettes (séparées par des virgules)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(species, parseTags(tagsText), selected) },
            enabled = changed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enregistrer espèce & étiquettes")
        }
    }
}

/**
 * Toggle « Publier sur mon flux d'amis » (NODE-137) : bascule la visibilité de
 * la fleur en 'friends' et, le cas échéant, l'inclusion de la position GPS. Le
 * changement est persisté localement puis synchronisé.
 */
@Composable
private fun FeedPublicationSection(
    flowerId: Long,
    published: Boolean,
    includeGps: Boolean,
    onSetPublication: (Boolean, Boolean) -> Unit,
) {
    var isPublished by remember(flowerId, published) { mutableStateOf(published) }
    var gps by remember(flowerId, includeGps) { mutableStateOf(includeGps) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Publier sur mon flux d'amis",
                style = MaterialTheme.typography.bodyLarge,
            )
            Switch(
                checked = isPublished,
                onCheckedChange = {
                    isPublished = it
                    onSetPublication(it, gps)
                },
            )
        }
        if (isPublished) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Inclure la position GPS",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(
                    checked = gps,
                    onCheckedChange = {
                        gps = it
                        onSetPublication(true, it)
                    },
                )
            }
            Text(
                text = "Vos amis verront cette fleur dans leur flux.",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Liste déroulante des suggestions d'espèces (référentiel). */
@Composable
private fun SpeciesSuggestions(
    suggestions: List<SpeciesDto>,
    onPick: (SpeciesDto) -> Unit,
) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            suggestions.forEachIndexed { index, species ->
                if (index > 0) HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(species) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "${species.emoji ?: "🌸"} ${species.scientificName}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "${species.commonName} · ${species.family}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** Découpe une saisie « a, b ,c » en liste nettoyée, sans doublons ni vides. */
private fun parseTags(raw: String): List<String> =
    raw.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

/**
 * Mini-carte interactive (NODE-11) : situe la fleur sur une carte MapLibre
 * (tuiles MapTiler) centrée sur sa position, avec un marqueur emoji d'espèce.
 * Réutilise l'infrastructure de la fonctionnalité Carte. Sans clé MapTiler, on
 * retombe sur un aperçu textuel des coordonnées.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun MiniMap(point: GeoPoint, emoji: String) {
    val apiKey = BuildConfig.MAPTILER_API_KEY
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        if (apiKey.isBlank()) {
            MiniMapFallback(point)
            return@Card
        }

        val target = remember(point.latitude, point.longitude) {
            LatLng(point.latitude, point.longitude)
        }
        val mapView = rememberMapViewWithLifecycle()

        androidx.compose.runtime.LaunchedEffect(mapView, target, emoji) {
            mapView.getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(target)
                    .zoom(MINI_MAP_ZOOM)
                    .build()
                map.setStyle(mapTilerStyleUrl(apiKey)) { style ->
                    style.setupFlowerClustering()
                    style.updateFlowerMarkers(
                        listOf(
                            FlowerMarker(
                                id = 0L,
                                latitude = point.latitude,
                                longitude = point.longitude,
                                emoji = emoji,
                            ),
                        ),
                    )
                }
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                // Empêche la Column scrollable parente d'intercepter les gestes
                // tant qu'on manipule la carte (sinon le scroll vertical vole le
                // déplacement/zoom de la carte).
                mapView.apply {
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN ->
                                view.parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                view.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                }
            },
        )
    }
}

/** Zoom de la mini-carte du détail : assez serré pour situer la fleur. */
private const val MINI_MAP_ZOOM = 14.0

/** Aperçu textuel des coordonnées quand la carte ne peut pas s'afficher. */
@Composable
private fun MiniMapFallback(point: GeoPoint) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("🗺️", style = MaterialTheme.typography.headlineMedium)
            Text(
                text = "${"%.5f".format(point.latitude)}, " +
                    "%.5f".format(point.longitude),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Carte indisponible (clé MapTiler manquante)",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun NotesEditor(
    flowerId: Long,
    initialNotes: String,
    onSave: (String) -> Unit,
) {
    // Réinitialise le champ quand on change de fleur.
    var notes by remember(flowerId) { mutableStateOf(initialNotes) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Button(
            onClick = { onSave(notes) },
            enabled = notes != initialNotes,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enregistrer les notes")
        }
    }
}

/** Convertit les colonnes GPS en [GeoPoint], ou null si la position manque. */
private fun FlowerEntity.toGeoPoint(): GeoPoint? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return GeoPoint(lat, lng, accuracyMeters ?: 0f)
}
