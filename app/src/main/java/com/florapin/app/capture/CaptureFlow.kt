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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.florapin.app.data.FlowerRepository
import com.florapin.app.location.GeoPoint
import com.florapin.app.location.LocationProvider
import com.florapin.app.permission.AppPermission
import com.florapin.app.permission.PermissionsScreen
import com.florapin.app.permission.isGranted
import com.florapin.app.permission.rememberMultiplePermissionsState
import com.florapin.app.sync.SyncScheduler

/** État de la récupération GPS pour la capture courante. */
private sealed interface LocationState {
    data object Loading : LocationState
    data class Available(val point: GeoPoint) : LocationState
    data object Unavailable : LocationState
}

/**
 * Flux complet de capture (NODE-6 + NODE-7) :
 * 1. s'assure que la permission caméra est accordée (la localisation est
 *    demandée en même temps mais reste optionnelle) ;
 * 2. affiche l'aperçu caméra ;
 * 3. après la prise, récupère la position et montre la photo + ses coordonnées.
 */
@Composable
fun CaptureFlow(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val (permissions, requestPermissions) = rememberMultiplePermissionsState(
        permissions = listOf(AppPermission.CAMERA, AppPermission.LOCATION),
    )
    val cameraGranted = permissions.statuses[AppPermission.CAMERA]?.isGranted == true

    val locationProvider = remember(context) { LocationProvider(context) }
    val repository = remember(context) { FlowerRepository.from(context) }

    var capturedUri: Uri? by remember { mutableStateOf(null) }
    var locationState: LocationState by remember { mutableStateOf(LocationState.Loading) }

    // Récupère la position puis persiste la fleur dès qu'une photo est prise.
    LaunchedEffect(capturedUri) {
        val uri = capturedUri ?: return@LaunchedEffect
        locationState = LocationState.Loading
        val point = runCatching { locationProvider.currentLocation() }.getOrNull()
        locationState = if (point != null) {
            LocationState.Available(point)
        } else {
            LocationState.Unavailable
        }
        uri.path?.let { path ->
            runCatching { repository.saveCapture(imagePath = path, location = point) }
                // Tente une sync immédiate : no-op si la synchronisation cloud est
                // désactivée (la photo reste alors uniquement sur l'appareil).
                .onSuccess { SyncScheduler.syncNow(context) }
        }
    }

    when {
        !cameraGranted -> {
            PermissionsScreen(
                state = permissions,
                onRequest = requestPermissions,
                modifier = modifier,
            )
        }

        capturedUri == null -> {
            CameraScreen(
                onPhotoSaved = { uri -> capturedUri = uri },
                modifier = modifier,
            )
        }

        else -> {
            CapturedPhotoScreen(
                uri = capturedUri!!,
                locationState = locationState,
                onRetake = { capturedUri = null },
                onFinished = onFinished,
                modifier = modifier,
            )
        }
    }
}

/** Aperçu de la photo enregistrée + coordonnées GPS associées. */
@Composable
private fun CapturedPhotoScreen(
    uri: Uri,
    locationState: LocationState,
    onRetake: () -> Unit,
    onFinished: () -> Unit,
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
                model = uri,
                contentDescription = "Photo capturée",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Évite que la barre de navigation système recouvre les boutons
                // « Reprendre / Terminer » sur les appareils en mode 3 boutons.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Photo enregistrée dans la galerie ✅",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = uri.lastPathSegment ?: uri.toString(),
                style = MaterialTheme.typography.bodySmall,
            )
            LocationLine(locationState)
            Button(
                onClick = onRetake,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reprendre une photo")
            }
            OutlinedButton(
                onClick = onFinished,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Terminer")
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
