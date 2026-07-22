package com.florapin.app.detail

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.florapin.app.BuildConfig
import com.florapin.app.R
import com.florapin.app.albums.AddToAlbumSheet
import com.florapin.app.capture.CameraScreen
import com.florapin.app.data.FlowerEntity
import com.florapin.app.data.PhotoEntity
import com.florapin.app.data.imageModel
import com.florapin.app.data.thumbnailModel
import com.florapin.app.geo.PlaceNameResolver
import com.florapin.app.identify.IdentificationRequestSection
import com.florapin.app.identify.IdentificationRequestViewModel
import com.florapin.app.likes.LikeButton
import com.florapin.app.likes.LikeViewModel
import com.florapin.app.location.GeoPoint
import com.florapin.app.map.FlowerMarker
import com.florapin.app.map.MapLayers
import com.florapin.app.map.emojiToBitmap
import com.florapin.app.map.mapTilerStyleUrl
import com.florapin.app.map.rememberMapViewWithLifecycle
import com.florapin.app.map.setupFlowerClustering
import com.florapin.app.map.updateFlowerMarkers
import com.florapin.app.network.dto.SpeciesDto
import com.florapin.app.share.ShareFlowerSheet
import com.florapin.app.ui.components.BotanicalIcon
import com.florapin.app.ui.components.FullscreenPhotoViewer
import com.florapin.app.ui.layout.topBarHeight
import com.florapin.app.ui.transition.FloraSharedScope
import com.florapin.app.ui.transition.sharedFlowerImage
import com.florapin.app.util.formatCaptureDate
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import java.io.File
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Détail d'une fleur avec navigation par balayage (TÂCHE 6.10) : un
 * [HorizontalPager] permet de passer d'une fleur à l'autre en glissant
 * horizontalement, dans l'ordre de la galerie. On ne navigue pas fleur par
 * fleur : la liste ordonnée d'ids est fournie d'un bloc au pager (device-first,
 * même source que la galerie), et chaque page observe sa propre fleur.
 *
 * Tant que la liste n'est pas chargée (ou si la fleur n'y figure pas), on affiche
 * directement la page seule — le balayage s'active dès que la liste est prête.
 */
@Composable
fun DetailScreen(
    flowerId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSpecies: (String) -> Unit = {},
    onOpenFlower: (Long) -> Unit = {},
    // Suppression annulable (TÂCHE 6.13) : reçoit l'id soft-supprimé pour que
    // l'écran précédent (galerie) propose l'annulation. Par défaut, revient en
    // arrière (compat lorsqu'aucun hôte de snackbar ne traite l'annulation).
    onDeleted: (Long) -> Unit = { onBack() },
    // Transitions partagées galerie ↔ détail (TÂCHE 6.17) : null hors navigation.
    sharedScope: FloraSharedScope? = null,
    pagerViewModel: DetailPagerViewModel = viewModel(),
) {
    val allFlowers by pagerViewModel.flowers.collectAsStateWithLifecycle()
    val orderedIds by pagerViewModel.orderedIds.collectAsStateWithLifecycle()
    val startIndex = orderedIds.indexOf(flowerId)

    if (startIndex < 0) {
        // Liste pas encore chargée depuis Room (ou fleur absente) : page unique.
        // Les ViewModels par fleur sont keyés sur l'id, donc l'instance créée ici
        // est réutilisée telle quelle par la page correspondante du pager.
        FlowerDetailPage(
            flowerId = flowerId,
            onBack = onBack,
            onDeleted = onDeleted,
            onOpenSpecies = onOpenSpecies,
            allFlowers = allFlowers,
            onOpenFlower = onOpenFlower,
            sharedScope = sharedScope,
            modifier = modifier,
        )
        return
    }

    val pagerState = rememberPagerState(initialPage = startIndex) { orderedIds.size }
    val coroutineScope = rememberCoroutineScope()
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        // Clé stable par fleur : le pager survit aux évolutions de la liste
        // (capture, suppression synchronisée…) sans mélanger les pages.
        key = { page -> orderedIds[page] },
    ) { page ->
        val id = orderedIds[page]
        FlowerDetailPage(
            flowerId = id,
            onBack = onBack,
            onDeleted = onDeleted,
            onOpenSpecies = onOpenSpecies,
            allFlowers = allFlowers,
            onOpenFlower = { nearbyFlowerId ->
                val targetPage = orderedIds.indexOf(nearbyFlowerId)
                if (targetPage >= 0) {
                    coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                } else {
                    onOpenFlower(nearbyFlowerId)
                }
            },
            // Seule la page ouverte au démarrage (même id que la vignette
            // tapée) porte l'élément partagé : les pages voisines du balayage
            // n'ont pas de vis-à-vis dans la galerie et s'affichent normalement.
            sharedScope = sharedScope.takeIf { page == startIndex },
        )
    }
}

/**
 * Détail d'une fleur (NODE-10) : photo, coordonnées, mini-carte, notes éditables
 * et suppression. Une page du pager du détail (TÂCHE 6.10) : les ViewModels par
 * fleur sont keyés sur [flowerId] pour que chaque page conserve son propre état,
 * y compris lorsque deux pages coexistent pendant un balayage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowerDetailPage(
    flowerId: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSpecies: (String) -> Unit = {},
    allFlowers: List<FlowerEntity> = emptyList(),
    onOpenFlower: (Long) -> Unit = {},
    onDeleted: (Long) -> Unit = { onBack() },
    sharedScope: FloraSharedScope? = null,
    viewModel: DetailViewModel = viewModel(key = "detail-$flowerId"),
    photosViewModel: PhotosViewModel = viewModel(key = "photos-$flowerId"),
    speciesPicker: SpeciesPickerViewModel = viewModel(
        key = "species-$flowerId",
        factory = SpeciesPickerViewModel.factory(LocalContext.current),
    ),
    identificationVm: IdentificationRequestViewModel = viewModel(
        key = "identify-$flowerId",
        factory = IdentificationRequestViewModel.factory(LocalContext.current),
    ),
    proposalsVm: ReceivedProposalsViewModel = viewModel(
        key = "proposals-$flowerId",
        factory = ReceivedProposalsViewModel.factory(LocalContext.current),
    ),
    likeVm: LikeViewModel = viewModel(
        key = "like-$flowerId",
        factory = LikeViewModel.factory(LocalContext.current),
    ),
    commentsVm: CommentsViewModel = viewModel(
        key = "comments-$flowerId",
        factory = CommentsViewModel.factory(LocalContext.current),
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
        serverId?.let(proposalsVm::load)
        serverId?.let(commentsVm::bind)
    }
    var showShare by remember { mutableStateOf(false) }
    var showAddToAlbum by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // Liste des likers ouverte en bottom sheet (tap sur le compteur de cœurs).
    var showLikers by remember { mutableStateOf(false) }

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
                expandedHeight = topBarHeight,
                title = { Text("Détail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BotanicalIcon(R.drawable.ic_back_botanical, "Retour")
                    }
                },
                actions = {
                    val current = flower
                    if (current != null) {
                        val context = LocalContext.current
                        IconButton(onClick = { showAddToAlbum = true }) {
                            BotanicalIcon(
                                R.drawable.ic_nav_albums,
                                "Ajouter à un album",
                            )
                        }
                        IconButton(onClick = { showShare = true }) {
                            BotanicalIcon(R.drawable.ic_share_botanical, "Partager")
                        }
                        // Suppression (destructive) reléguée dans un menu de
                        // débordement pour éviter un toucher accidentel à côté des
                        // actions courantes ; une confirmation suit de toute façon.
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                BotanicalIcon(
                                    R.drawable.ic_more_botanical,
                                    "Plus d'options",
                                )
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false },
                            ) {
                                // Partage externe de la photo (TÂCHE 6.12) vers
                                // une autre application (via FileProvider).
                                DropdownMenuItem(
                                    text = { Text("Partager la photo") },
                                    leadingIcon = {
                                        BotanicalIcon(
                                            R.drawable.ic_photo_botanical,
                                            contentDescription = null,
                                            size = 24.dp,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        shareFlowerPhoto(context, current)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Supprimer") },
                                    leadingIcon = {
                                        BotanicalIcon(
                                            R.drawable.ic_delete_botanical,
                                            contentDescription = null,
                                            size = 24.dp,
                                        )
                                    },
                                    onClick = {
                                        menuOpen = false
                                        showDeleteConfirm = true
                                    },
                                )
                            }
                        }
                    }
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
                sharedScope = sharedScope,
                speciesPicker = speciesPicker,
                identificationVm = identificationVm,
                proposalsVm = proposalsVm,
                onSaveNotes = viewModel::saveNotes,
                onSaveClassification = viewModel::saveClassification,
                likeState = likeState,
                onToggleLike = likeVm::toggle,
                onReact = likeVm::react,
                onOpenLikers = { showLikers = true },
                commentsVm = commentsVm,
                onOpenSpecies = onOpenSpecies,
                allFlowers = allFlowers,
                onOpenFlower = onOpenFlower,
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

    // Fleur synchronisée requise : les likers vivent côté serveur.
    val likersServerId = flower?.serverId
    if (showLikers && likersServerId != null) {
        com.florapin.app.likes.LikersBottomSheet(
            flowerServerId = likersServerId,
            onDismiss = { showLikers = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_flower_title)) },
            text = { Text(stringResource(R.string.delete_flower_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.delete(onDeleted = onDeleted)
                    },
                ) { Text(stringResource(R.string.delete_flower_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.delete_flower_cancel))
                }
            },
        )
    }
}

@Composable
private fun DetailContent(
    flower: FlowerEntity,
    photos: List<PhotoEntity>,
    sharedScope: FloraSharedScope? = null,
    speciesPicker: SpeciesPickerViewModel,
    identificationVm: IdentificationRequestViewModel,
    proposalsVm: ReceivedProposalsViewModel,
    onSaveNotes: (String) -> Unit,
    onSaveClassification: (String, List<String>, SpeciesDto?) -> Unit,
    likeState: com.florapin.app.likes.LikeState,
    onToggleLike: () -> Unit,
    onReact: (String) -> Unit,
    onOpenLikers: () -> Unit,
    commentsVm: CommentsViewModel,
    onOpenSpecies: (String) -> Unit,
    allFlowers: List<FlowerEntity>,
    onOpenFlower: (Long) -> Unit,
    onAddPhoto: () -> Unit,
    onDeletePhoto: (PhotoEntity) -> Unit,
    onMakeCover: (PhotoEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Modèles d'images pour la visionneuse plein écran : couverture puis photos
    // additionnelles, dans l'ordre affiché.
    val viewerModels = remember(flower.imagePath, flower.remoteImageUrl, photos) {
        listOf(flower.imageModel()) + photos.map { it.imageModel() }
    }
    var viewerStart by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = flower.imageModel(),
            contentDescription = "Photo de la fleur (toucher pour agrandir)",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                // Élément partagé depuis la vignette de la galerie (TÂCHE 6.17) ;
                // no-op sans portée de transition.
                .sharedFlowerImage(sharedScope, flower.id)
                .clickable { viewerStart = 0 },
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
                // index + 1 : la couverture occupe la position 0 dans la visionneuse.
                onOpenPhoto = { index -> viewerStart = index + 1 },
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
                        myReaction = likeState.myReaction,
                        count = likeState.count,
                        onToggle = onToggleLike,
                        onReact = onReact,
                        onCountClick = onOpenLikers.takeIf { likeState.count > 0 },
                    )
                }
            }

            val point = flower.toGeoPoint()
            if (point != null) {
                val nearbyFlowers = remember(flower.id, point, allFlowers) {
                    findNearbyFlowers(flower, allFlowers)
                }
                DetailLocationLabel(point)
                MiniMap(
                    point = point,
                    flowerId = flower.id,
                    nearbyFlowers = nearbyFlowers,
                    onFlowerClick = onOpenFlower,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BotanicalIcon(
                        R.drawable.ic_nav_map,
                        contentDescription = null,
                        size = 22.dp,
                    )
                    Text(
                        text = "Position non enregistrée",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            ClassificationEditor(
                flowerId = flower.id,
                initialSpecies = flower.species.orEmpty(),
                speciesPicker = speciesPicker,
                // Les anciennes étiquettes restent conservées dans les données,
                // mais ne sont plus exposées dans cette interface.
                onSave = { species, selected ->
                    onSaveClassification(species, flower.tags, selected)
                },
            )

            // Lien vers la fiche d'espèce quand la fleur est rattachée (NODE-151).
            flower.speciesId?.let { speciesId ->
                val label = flower.speciesCommonName
                    ?: flower.speciesScientificName
                    ?: flower.species
                    ?: "cette espèce"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSpecies(speciesId) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BotanicalIcon(
                        R.drawable.ic_identify_botanical,
                        contentDescription = null,
                        size = 24.dp,
                    )
                    Text(
                        text = "Voir la fiche : $label",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Demande d'identification aux amis quand l'espèce est absente (NODE-134).
            // Nécessite une fleur déjà synchronisée (serverId), car la demande est
            // adressée aux amis via l'API.
            if (flower.species.isNullOrBlank()) {
                IdentificationRequestSection(
                    flowerServerId = flower.serverId,
                    viewModel = identificationVm,
                )
                // Propositions reçues des amis (NODE-134) : visibles et acceptables
                // tant que la fleur n'est pas identifiée. L'acceptation applique
                // l'espèce localement (device-first) en plus du serveur.
                ReceivedProposalsSection(
                    viewModel = proposalsVm,
                    onAccept = { proposal ->
                        flower.serverId?.let { sid ->
                            proposalsVm.accept(sid, proposal) { species ->
                                onSaveClassification(species, flower.tags, null)
                            }
                        }
                    },
                    onReject = { proposal ->
                        flower.serverId?.let { sid ->
                            proposalsVm.reject(sid, proposal)
                        }
                    },
                    onThank = { proposal ->
                        flower.serverId?.let { sid ->
                            proposalsVm.thank(sid, proposal)
                        }
                    },
                )
            }

            NotesEditor(
                flowerId = flower.id,
                initialNotes = flower.notes,
                onSave = onSaveNotes,
            )

            // Fil de discussion : disponible une fois la fleur synchronisée
            // (les commentaires vivent côté serveur, comme les cœurs). Tant que
            // ce n'est pas le cas, on affiche une invitation à synchroniser
            // plutôt que de masquer la section.
            if (flower.serverId != null) {
                CommentsSection(viewModel = commentsVm)
            } else {
                CommentsLockedNotice()
            }
        }
    }

    viewerStart?.let { start ->
        FullscreenPhotoViewer(
            models = viewerModels,
            startIndex = start,
            onDismiss = { viewerStart = null },
        )
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
    onOpenPhoto: (Int) -> Unit,
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
            Button(onClick = onAddPhoto) {
                BotanicalIcon(
                    R.drawable.ic_add_botanical,
                    contentDescription = null,
                    size = 22.dp,
                )
                Text("Ajouter une photo", modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(photos, key = { _, p -> p.id }) { index, photo ->
                    PhotoThumbnail(
                        photo = photo,
                        onOpen = { onOpenPhoto(index) },
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
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onMakeCover: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = photo.thumbnailModel(),
            contentDescription = "Photo supplémentaire (toucher pour agrandir)",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpen),
        )
        Row {
            // « Définir comme couverture » seulement si la photo a un fichier local.
            if (photo.imagePath.isNotEmpty()) {
                IconButton(onClick = onMakeCover, modifier = Modifier.size(48.dp)) {
                    BotanicalIcon(
                        R.drawable.ic_cover_botanical,
                        "Définir comme couverture",
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                BotanicalIcon(
                    R.drawable.ic_delete_botanical,
                    "Supprimer cette photo",
                )
            }
        }
    }
}

/**
 * Édition de l'espèce, autocomplétée sur le référentiel (NODE-150).
 *
 * Le champ espèce interroge /species/search ; sélectionner une suggestion fixe
 * le nom scientifique et rattache la fleur (species_id). Une saisie libre reste
 * possible : aucune fiche n'est alors associée.
 */
@Composable
private fun ClassificationEditor(
    flowerId: Long,
    initialSpecies: String,
    speciesPicker: SpeciesPickerViewModel,
    onSave: (String, SpeciesDto?) -> Unit,
) {
    // Clés incluant la valeur initiale : le champ se resynchronise quand l'espèce
    // change de l'extérieur (ex. acceptation d'une proposition d'ami). Comme
    // initialSpecies ne bouge qu'à la sauvegarde/acceptation, la frappe en cours
    // n'est pas perturbée.
    var species by remember(flowerId, initialSpecies) { mutableStateOf(initialSpecies) }
    // Fiche du référentiel sélectionnée ; null tant que l'utilisateur tape librement.
    var selected by remember(flowerId, initialSpecies) { mutableStateOf<SpeciesDto?>(null) }
    val suggestions by speciesPicker.suggestions.collectAsStateWithLifecycle()

    val changed = species != initialSpecies || selected != null

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
        Button(
            onClick = { onSave(species, selected) },
            enabled = changed,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Enregistrer l'espèce")
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

/**
 * Mini-carte interactive (NODE-11) : situe la fleur sur une carte MapLibre
 * (tuiles MapTiler) centrée sur sa position, avec les autres observations proches.
 * Réutilise l'infrastructure de la fonctionnalité Carte. Sans clé MapTiler, on
 * retombe sur un aperçu textuel des coordonnées.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun MiniMap(
    point: GeoPoint,
    flowerId: Long,
    nearbyFlowers: List<FlowerEntity>,
    onFlowerClick: (Long) -> Unit,
) {
    val apiKey = BuildConfig.MAPTILER_API_KEY
    val context = LocalContext.current
    val mapStyle = remember { com.florapin.app.map.MapStylePreferences(context).get() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        ) {
            if (apiKey.isBlank()) {
                MiniMapFallback(point)
            } else {
                MiniMapView(
                    point = point,
                    flowerId = flowerId,
                    nearbyFlowers = nearbyFlowers,
                    apiKey = apiKey,
                    mapStyle = mapStyle,
                    onFlowerClick = onFlowerClick,
                )
            }
        }
        // Menu superposé (NODE-6.11) : ouvrir dans Maps / copier les coordonnées.
        MiniMapMenu(point, modifier = Modifier.align(Alignment.TopEnd))
    }
}

/**
 * Contenu MapLibre de la mini-carte (extrait de [MiniMap] pour que la carte et
 * son menu superposé coexistent dans un même [Box]).
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun MiniMapView(
    point: GeoPoint,
    flowerId: Long,
    nearbyFlowers: List<FlowerEntity>,
    apiKey: String,
    mapStyle: com.florapin.app.map.MapStyle,
    onFlowerClick: (Long) -> Unit,
) {
    val target = remember(point.latitude, point.longitude) {
        LatLng(point.latitude, point.longitude)
    }
    val mapView = rememberMapViewWithLifecycle()
    val currentOnFlowerClick by rememberUpdatedState(onFlowerClick)
    val currentFlowerId by rememberUpdatedState(flowerId)
    val markers = remember(flowerId, point, nearbyFlowers) {
        buildList {
            add(
                FlowerMarker(
                    id = flowerId,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    emoji = MINI_MAP_CURRENT_ICON,
                    navigable = true,
                ),
            )
            nearbyFlowers.forEach { nearby ->
                add(
                    FlowerMarker(
                        id = nearby.id,
                        latitude = nearby.latitude ?: return@forEach,
                        longitude = nearby.longitude ?: return@forEach,
                        emoji = MINI_MAP_NEARBY_ICON,
                        navigable = true,
                    ),
                )
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val hitArea = RectF(
                    screenPoint.x - MINI_MAP_TOUCH_SLOP,
                    screenPoint.y - MINI_MAP_TOUCH_SLOP,
                    screenPoint.x + MINI_MAP_TOUCH_SLOP,
                    screenPoint.y + MINI_MAP_TOUCH_SLOP,
                )
                val clickedId = map.queryRenderedFeatures(hitArea, MapLayers.UNCLUSTERED)
                    .firstOrNull { it.hasProperty(MapLayers.PROP_ID) }
                    ?.getNumberProperty(MapLayers.PROP_ID)
                    ?.toLong()
                if (clickedId != null && clickedId != currentFlowerId) {
                    currentOnFlowerClick(clickedId)
                    true
                } else {
                    false
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(mapView, target, markers, mapStyle) {
        mapView.getMapAsync { map ->
            map.cameraPosition = CameraPosition.Builder()
                .target(target)
                .zoom(MINI_MAP_ZOOM)
                .build()
            map.setStyle(mapTilerStyleUrl(apiKey, mapStyle)) { style ->
                style.setupFlowerClustering(mapView.context)
                style.addImage(MINI_MAP_CURRENT_ICON, emojiToBitmap("📍", 84))
                style.addImage(MINI_MAP_NEARBY_ICON, emojiToBitmap("📷", 68))
                style.updateFlowerMarkers(markers)
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

private fun findNearbyFlowers(
    current: FlowerEntity,
    flowers: List<FlowerEntity>,
): List<FlowerEntity> {
    val latitude = current.latitude ?: return emptyList()
    val longitude = current.longitude ?: return emptyList()
    return flowers.asSequence()
        .filter { it.id != current.id && it.latitude != null && it.longitude != null }
        .map { flower ->
            flower to distanceMeters(
                latitude,
                longitude,
                flower.latitude!!,
                flower.longitude!!,
            )
        }
        .filter { (_, distance) -> distance <= MINI_MAP_NEARBY_RADIUS_METERS }
        .sortedBy { (_, distance) -> distance }
        .take(MINI_MAP_MAX_NEARBY_FLOWERS)
        .map { (flower, _) -> flower }
        .toList()
}

private fun distanceMeters(
    latitudeA: Double,
    longitudeA: Double,
    latitudeB: Double,
    longitudeB: Double,
): Double {
    val latitudeDelta = Math.toRadians(latitudeB - latitudeA)
    val longitudeDelta = Math.toRadians(longitudeB - longitudeA)
    val a = sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
        cos(Math.toRadians(latitudeA)) * cos(Math.toRadians(latitudeB)) *
        sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
}

/**
 * Menu d'actions de la mini-carte (TÂCHE 6.11) : ouvrir la position dans une
 * application de cartes (Intent `geo:`) ou copier les coordonnées décimales.
 * Superposé au coin de la carte pour ne pas gêner sa manipulation.
 */
@Composable
private fun MiniMapMenu(point: GeoPoint, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }) {
            BotanicalIcon(
                R.drawable.ic_more_botanical,
                "Options de localisation",
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Ouvrir dans Maps") },
                leadingIcon = {
                    BotanicalIcon(
                        R.drawable.ic_nav_map,
                        contentDescription = null,
                        size = 24.dp,
                    )
                },
                onClick = {
                    open = false
                    openPointInMaps(context, point)
                },
            )
            DropdownMenuItem(
                text = { Text("Copier les coordonnées") },
                leadingIcon = {
                    BotanicalIcon(
                        R.drawable.ic_copy_botanical,
                        contentDescription = null,
                        size = 24.dp,
                    )
                },
                onClick = {
                    open = false
                    copyPointCoordinates(context, point)
                },
            )
        }
    }
}

/** Coordonnées décimales « lat,lng » (Locale.US) pour URI `geo:` et copie. */
private fun GeoPoint.toLatLngString(): String =
    String.format(java.util.Locale.US, "%.6f,%.6f", latitude, longitude)

/**
 * Ouvre la position dans une application de cartes via un Intent `geo:` avec
 * un repère (`?q=`). Prévient si aucune application ne gère l'intent.
 */
private fun openPointInMaps(context: Context, point: GeoPoint) {
    val coords = point.toLatLngString()
    val uri = Uri.parse("geo:$coords?q=$coords")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune application de cartes", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Partage externe de la photo de couverture (TÂCHE 6.12) vers une autre
 * application (messagerie, réseaux…). Les photos vivant en stockage privé
 * (PhotoStorage → filesDir/photos), on passe par le [FileProvider] déclaré au
 * manifeste pour concéder un accès temporaire en lecture (URI content://).
 *
 * Device-first : sans fichier local (fleur seulement distante, non mise en
 * cache), rien à partager — on prévient plutôt que d'échouer silencieusement.
 */
private fun shareFlowerPhoto(context: Context, flower: FlowerEntity) {
    val path = flower.imagePath
    if (path.isEmpty()) {
        Toast.makeText(context, "Photo non disponible hors-ligne", Toast.LENGTH_SHORT).show()
        return
    }
    val file = File(path)
    if (!file.exists()) {
        Toast.makeText(context, "Photo introuvable", Toast.LENGTH_SHORT).show()
        return
    }
    val uri: Uri = try {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    } catch (_: IllegalArgumentException) {
        Toast.makeText(context, "Partage impossible", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Partager la photo"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune application de partage", Toast.LENGTH_SHORT).show()
    }
}

/** Copie les coordonnées décimales dans le presse-papiers et le signale. */
private fun copyPointCoordinates(context: Context, point: GeoPoint) {
    val coords = point.toLatLngString()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Coordonnées", coords))
    Toast.makeText(context, "Coordonnées copiées", Toast.LENGTH_SHORT).show()
}

/** Zoom de la mini-carte du détail : assez serré pour distinguer les observations. */
private const val MINI_MAP_ZOOM = 16.0
private const val MINI_MAP_NEARBY_RADIUS_METERS = 500.0
private const val MINI_MAP_MAX_NEARBY_FLOWERS = 20
private const val MINI_MAP_TOUCH_SLOP = 18f
private const val MINI_MAP_CURRENT_ICON = "detail-current-flower"
private const val MINI_MAP_NEARBY_ICON = "detail-nearby-flower"
private const val EARTH_RADIUS_METERS = 6_371_000.0

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
            BotanicalIcon(
                R.drawable.ic_nav_map,
                contentDescription = null,
                size = 48.dp,
            )
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

/** Nom de ville/région résolu avec la même logique que le flux partagé. */
@Composable
private fun DetailLocationLabel(point: GeoPoint) {
    val context = LocalContext.current
    var placeName by remember(context, point.latitude, point.longitude) {
        mutableStateOf("Localisation…")
    }
    LaunchedEffect(context, point.latitude, point.longitude) {
        placeName = PlaceNameResolver.resolve(context, point.latitude, point.longitude)
            ?: "Lieu non identifié"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BotanicalIcon(
            R.drawable.ic_nav_map,
            contentDescription = null,
            size = 22.dp,
        )
        Text(
            text = placeName,
            style = MaterialTheme.typography.bodyMedium,
        )
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
