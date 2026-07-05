package com.florapin.app.capture

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.annotation.VisibleForTesting
import coil.compose.AsyncImage
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.PhotoRepository
import com.florapin.app.location.GeoPoint
import com.florapin.app.location.LocationProvider
import com.florapin.app.permission.AppPermission
import com.florapin.app.permission.PermissionsScreen
import com.florapin.app.permission.isGranted
import com.florapin.app.permission.rememberMultiplePermissionsState
import com.florapin.app.sync.SyncScheduler
import kotlinx.coroutines.launch
import java.io.File

/**
 * État de la récupération GPS pour la capture courante.
 *
 * `internal` (et non `private`) pour permettre aux tests UI de piloter directement
 * l'écran de revue [CapturedPhotoScreen] avec une source d'image factice, sans
 * caméra réelle (voir `CaptureFlowTest`).
 */
internal sealed interface LocationState {
    data object Loading : LocationState
    data class Available(val point: GeoPoint) : LocationState
    data object Unavailable : LocationState
}

/**
 * Photo qui vient d'être prise et est en cours de revue. La première photo d'un
 * groupe est la couverture (portée par la fleur) ; les suivantes sont des photos
 * additionnelles (table `flower_photos`).
 *
 * `internal` pour la testabilité (cf. [LocationState]).
 */
internal sealed interface Captured {
    val uri: Uri

    data class Cover(override val uri: Uri) : Captured
    data class Added(override val uri: Uri, val photoId: Long) : Captured
}

/**
 * Flux complet de capture (NODE-6 + NODE-7) :
 * 1. s'assure que la permission caméra est accordée (la localisation est
 *    demandée en même temps mais reste optionnelle) ;
 * 2. affiche l'aperçu caméra ;
 * 3. après chaque prise, propose d'**annuler** la photo, d'en **ajouter** une
 *    autre au même groupe (même fleur), ou de **terminer**.
 *
 * La synchronisation cloud est déclenchée à « Terminer » : on pousse alors la
 * fleur et toutes ses photos en une fois (et une annulation avant n'a donc rien
 * envoyé au serveur).
 */
@Composable
fun CaptureFlow(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val (permissions, requestPermissions) = rememberMultiplePermissionsState(
        permissions = listOf(AppPermission.CAMERA, AppPermission.LOCATION),
    )
    val cameraGranted = permissions.statuses[AppPermission.CAMERA]?.isGranted == true

    val locationProvider = remember(context) { LocationProvider(context) }
    val flowerRepo = remember(context) { FlowerRepository.from(context) }
    val photoRepo = remember(context) { PhotoRepository.from(context) }

    // Identifiant de la fleur du groupe en cours (null tant qu'aucune photo prise).
    var flowerId: Long? by remember { mutableStateOf(null) }
    var photoCount by remember { mutableIntStateOf(0) }
    // URI brut renvoyé par la caméra, en attente de persistance.
    var pendingUri: Uri? by remember { mutableStateOf(null) }
    // Photo actuellement en revue (null => on affiche la caméra).
    var captured: Captured? by remember { mutableStateOf(null) }
    var locationState: LocationState by remember { mutableStateOf(LocationState.Loading) }

    // Persiste la photo prise : crée la fleur (couverture) à la première, sinon
    // rattache une photo additionnelle au groupe.
    LaunchedEffect(pendingUri) {
        val uri = pendingUri ?: return@LaunchedEffect
        val path = uri.path
        if (path == null) {
            pendingUri = null
            return@LaunchedEffect
        }
        val existing = flowerId
        if (existing == null) {
            locationState = LocationState.Loading
            val point = runCatching { locationProvider.currentLocation() }.getOrNull()
            locationState = if (point != null) {
                LocationState.Available(point)
            } else {
                LocationState.Unavailable
            }
            val id = runCatching {
                flowerRepo.saveCapture(imagePath = path, location = point)
            }.getOrNull()
            if (id != null) {
                flowerId = id
                photoCount = 1
                captured = Captured.Cover(uri)
            }
        } else {
            val photoId = runCatching {
                photoRepo.addLocalPhoto(existing, path)
            }.getOrNull()
            if (photoId != null) {
                photoCount += 1
                captured = Captured.Added(uri, photoId)
            }
        }
        pendingUri = null
    }

    when {
        !cameraGranted -> {
            PermissionsScreen(
                state = permissions,
                onRequest = requestPermissions,
                modifier = modifier,
            )
        }

        captured == null -> {
            CameraScreen(
                onPhotoSaved = { uri -> pendingUri = uri },
                modifier = modifier,
            )
        }

        else -> {
            CapturedPhotoScreen(
                captured = captured!!,
                photoCount = photoCount,
                locationState = locationState,
                onCancelPhoto = {
                    val c = captured
                    if (c != null) scope.launch {
                        when (c) {
                            is Captured.Cover -> {
                                // Annule entièrement la capture (fleur mono-photo).
                                flowerId?.let { id ->
                                    flowerRepo.getById(id)?.let { flowerRepo.delete(it) }
                                }
                                flowerId = null
                                photoCount = 0
                            }

                            is Captured.Added -> {
                                // Retire seulement cette photo additionnelle.
                                photoRepo.hardDelete(c.photoId)
                                photoCount = (photoCount - 1).coerceAtLeast(1)
                            }
                        }
                        c.uri.path?.let { runCatching { File(it).delete() } }
                        captured = null
                    }
                },
                onAddAnother = { captured = null },
                onFinish = {
                    scope.launch {
                        // Pousse la fleur et toutes ses photos d'un coup ; no-op si
                        // la synchronisation cloud est désactivée (reste sur l'appareil).
                        if (flowerId != null) SyncScheduler.syncNow(context)
                        onFinished()
                    }
                },
                modifier = modifier,
            )
        }
    }
}

/**
 * Aperçu de la photo prise + actions (annuler / ajouter au groupe / terminer).
 *
 * `internal` + [VisibleForTesting] : c'est le point d'entrée testable du flux de
 * capture (l'aperçu caméra CameraX n'est pas instrumentable sur émulateur).
 */
@VisibleForTesting
@Composable
internal fun CapturedPhotoScreen(
    captured: Captured,
    photoCount: Int,
    locationState: LocationState,
    onCancelPhoto: () -> Unit,
    onAddAnother: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = captured.uri,
                contentDescription = "Photo capturée",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Évite que la barre de navigation système recouvre les boutons
                // sur les appareils en mode 3 boutons.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "📸 $photoCount photo${if (photoCount > 1) "s" else ""} dans ce groupe",
                style = MaterialTheme.typography.titleMedium,
            )
            LocationLine(locationState)
            Button(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Terminer")
            }
            OutlinedButton(
                onClick = onAddAnother,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("➕ Ajouter une photo")
            }
            TextButton(
                onClick = onCancelPhoto,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (captured is Captured.Cover) {
                        "Annuler cette capture"
                    } else {
                        "Annuler cette photo"
                    },
                )
            }
        }
    }
}

@Composable
private fun LocationLine(state: LocationState) {
    val text = when (state) {
        LocationState.Loading -> "📍 Localisation en cours…"
        is LocationState.Available -> "📍 ${state.point.format()}"
        LocationState.Unavailable -> "📍 Localisation indisponible"
    }
    Text(text = text, style = MaterialTheme.typography.bodyMedium)
}
