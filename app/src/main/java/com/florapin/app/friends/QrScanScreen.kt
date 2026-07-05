package com.florapin.app.friends

import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.florapin.app.permission.AppPermission
import com.florapin.app.permission.PermissionStatus
import com.florapin.app.permission.openAppSettings
import com.florapin.app.permission.rememberMultiplePermissionsState
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "QrScanScreen"

/**
 * Scan d'un QR code d'ami (TÂCHE 4.5) : aperçu caméra plein écran + analyse ZXing
 * des trames. Au premier code décodé, [onScanned] est invoqué avec le contenu
 * brut (le décodage/validation FloraPin est fait plus haut, dans le ViewModel).
 *
 * La permission caméra a son propre flux ici (réutilise
 * [rememberMultiplePermissionsState] + [AppPermission.CAMERA]).
 */
@Composable
fun QrScanScreen(
    onScanned: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (permissionState, requestPermission) =
        rememberMultiplePermissionsState(listOf(AppPermission.CAMERA))
    val cameraStatus = permissionState.statuses[AppPermission.CAMERA]
        ?: PermissionStatus.DENIED

    Box(modifier = modifier.fillMaxSize()) {
        if (cameraStatus == PermissionStatus.GRANTED) {
            QrCameraPreview(onScanned = onScanned)
            ScanOverlay()
        } else {
            CameraPermissionGate(
                permanentlyDenied = cameraStatus == PermissionStatus.PERMANENTLY_DENIED,
                onRequest = requestPermission,
            )
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
        ) {
            Text("← Retour")
        }
    }
}

/** Aperçu caméra + analyseur ZXing lié au cycle de vie de l'écran. */
@Composable
private fun QrCameraPreview(onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(lifecycleOwner) {
        // Ne déclenche qu'une fois : évite d'empiler N demandes pour un même code.
        val handled = AtomicBoolean(false)
        val analyzer = QrAnalyzer { payload ->
            if (handled.compareAndSet(false, true)) {
                ContextCompat.getMainExecutor(context).execute { onScanned(payload) }
            }
        }
        controller.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(context),
            analyzer,
        )
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            PreviewView(ctx).apply { this.controller = controller }
        },
    )
}

/** Consigne d'usage superposée au bas de l'aperçu. */
@Composable
private fun ScanOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = Color.Black.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            Text(
                text = "Visez le QR code d'un ami pour l'ajouter.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(24.dp),
            )
        }
    }
}

/** Écran de demande de permission caméra (refus simple ou définitif). */
@Composable
private fun CameraPermissionGate(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = AppPermission.CAMERA.rationale,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (permanentlyDenied) {
            Text(
                text = "L'accès caméra a été refusé. Activez-le depuis les " +
                    "réglages de l'application.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { context.openAppSettings() }) {
                Text("Ouvrir les réglages")
            }
        } else {
            Button(onClick = onRequest) {
                Text("Autoriser la caméra")
            }
        }
    }
}

/**
 * Analyseur CameraX qui décode un QR code via ZXing depuis le plan de luminance
 * (Y) de la trame. Best-effort : toute trame illisible est simplement ignorée.
 */
private class QrAnalyzer(
    private val onDecoded: (String) -> Unit,
) : androidx.camera.core.ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override fun analyze(image: ImageProxy) {
        try {
            decode(image)?.let(onDecoded)
        } catch (e: Exception) {
            Log.v(TAG, "Trame non décodée", e)
        } finally {
            image.close()
        }
    }

    private fun decode(image: ImageProxy): String? {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val rowStride = plane.rowStride
        if (rowStride <= 0) return null
        val dataHeight = data.size / rowStride
        val width = minOf(image.width, rowStride)
        val height = minOf(image.height, dataHeight)
        if (width <= 0 || height <= 0) return null

        val source = PlanarYUVLuminanceSource(
            data, rowStride, dataHeight, 0, 0, width, height, false,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap).text
        } catch (_: Exception) {
            null
        } finally {
            reader.reset()
        }
    }
}
