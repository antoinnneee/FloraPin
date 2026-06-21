package com.florapin.app.capture

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.florapin.app.permission.AppPermission
import com.florapin.app.permission.PermissionsScreen
import com.florapin.app.permission.rememberMultiplePermissionsState

/**
 * Flux complet de capture (NODE-6) :
 * 1. s'assure que la permission caméra est accordée (sinon écran de demande) ;
 * 2. affiche l'aperçu caméra ;
 * 3. après la prise, montre la photo enregistrée avec une option « Reprendre ».
 */
@Composable
fun CaptureFlow(modifier: Modifier = Modifier) {
    val (cameraPermission, requestCamera) = rememberMultiplePermissionsState(
        permissions = listOf(AppPermission.CAMERA),
    )

    var capturedUri: Uri? by remember { mutableStateOf(null) }

    when {
        !cameraPermission.allGranted -> {
            PermissionsScreen(
                state = cameraPermission,
                onRequest = requestCamera,
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
                onRetake = { capturedUri = null },
                modifier = modifier,
            )
        }
    }
}

/** Aperçu de la photo qui vient d'être enregistrée. */
@Composable
private fun CapturedPhotoScreen(
    uri: Uri,
    onRetake: () -> Unit,
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Photo enregistrée ✅",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = uri.lastPathSegment ?: uri.toString(),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onRetake,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reprendre une photo")
            }
        }
    }
}
