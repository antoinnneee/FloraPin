package com.florapin.app.capture

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG = "CameraScreen"

/**
 * Aperçu caméra plein écran + bouton d'obturateur. À la prise, la photo est
 * enregistrée dans le stockage privé de l'app et [onPhotoSaved] est appelé avec
 * son [Uri].
 *
 * Suppose que la permission CAMERA est déjà accordée (gérée en amont).
 */
@Composable
fun CameraScreen(
    onPhotoSaved: (Uri) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        }
    }
    // Lie/délie le contrôleur au cycle de vie de l'écran.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    var isCapturing by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    this.controller = controller
                }
            },
        )

        Button(
            onClick = {
                if (isCapturing) return@Button
                isCapturing = true
                takePhoto(
                    controller = controller,
                    context = context,
                    onSaved = { uri ->
                        isCapturing = false
                        onPhotoSaved(uri)
                    },
                    onError = { exc ->
                        isCapturing = false
                        Log.e(TAG, "Échec de la capture", exc)
                        Toast.makeText(
                            context,
                            "Échec de la capture : ${exc.message}",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            },
            enabled = !isCapturing,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // Pousse le bouton au-dessus de la barre de navigation système
                // (3 boutons sur certains Xiaomi/MIUI), sinon elle le recouvre.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 32.dp)
                .size(80.dp),
        ) {
            Text(if (isCapturing) "…" else "📷")
        }
    }
}

/** Déclenche la capture et enregistre le JPEG dans le stockage privé. */
private fun takePhoto(
    controller: LifecycleCameraController,
    context: android.content.Context,
    onSaved: (Uri) -> Unit,
    onError: (ImageCaptureException) -> Unit,
) {
    val photoFile = PhotoStorage.newPhotoFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    controller.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                val uri = results.savedUri ?: Uri.fromFile(photoFile)
                onSaved(uri)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        },
    )
}
