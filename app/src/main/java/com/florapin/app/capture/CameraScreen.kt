package com.florapin.app.capture

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ZoomState
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.florapin.app.MainActivity
import com.florapin.app.R
import com.florapin.app.location.GpsFixState
import com.florapin.app.permission.findActivity
import com.florapin.app.util.Haptics
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

private const val TAG = "CameraScreen"

private val CameraInk = Color(0xFF0A0D0B)
private val CameraGlass = Color(0xD9141916)
private val CameraGlassLight = Color(0xE6252C27)
private val CameraWhite = Color(0xFFF4F7F3)
private val CameraMuted = Color(0xFFAEB8B0)
private val CameraGreen = Color(0xFFB8E0C6)
private val CameraAmber = Color(0xFFF6C453)
private val CameraDanger = Color(0xFFFFA69B)

private enum class CameraMode {
    CLASSIC,
    PRO,
}

private enum class FlashSetting(val captureMode: Int, val badge: String?) {
    OFF(ImageCapture.FLASH_MODE_OFF, null),
    AUTO(ImageCapture.FLASH_MODE_AUTO, "A"),
    ON(ImageCapture.FLASH_MODE_ON, "•"),
    ;

    fun next(): FlashSetting = entries[(ordinal + 1) % entries.size]
}

private enum class ProParameter(val shortLabel: String) {
    ISO("ISO"),
    SHUTTER("S"),
    FOCUS("F"),
    WHITE_BALANCE("WB"),
    EXPOSURE("EV"),
    ZOOM("ZM"),
}

private enum class WhiteBalance(
    val shortValue: String,
    val camera2Mode: Int,
) {
    AUTO("A", CaptureRequest.CONTROL_AWB_MODE_AUTO),
    TUNGSTEN("3200K", CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT),
    FLUORESCENT("4000K", CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT),
    DAYLIGHT("5200K", CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT),
    CLOUDY("6000K", CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
    SHADE("7000K", CaptureRequest.CONTROL_AWB_MODE_SHADE),
}

private data class ProCapabilities(
    val manualSensor: Boolean = false,
    val isoRange: IntRange = 100..3200,
    val exposureTimeRangeNanos: LongRange = 125_000L..1_000_000_000L,
    val maxFocusDiopters: Float = 0f,
    val exposureCompensationRange: IntRange = 0..0,
    val exposureCompensationStep: Float = 0f,
)

private data class ProSettings(
    val manualExposure: Boolean = false,
    val iso: Int = 100,
    val exposureTimeNanos: Long = 8_000_000L,
    val manualFocus: Boolean = false,
    val focusDiopters: Float = 0f,
    val whiteBalance: WhiteBalance = WhiteBalance.AUTO,
    val exposureCompensationIndex: Int = 0,
)

/**
 * Viseur FloraPin.
 *
 * Le mode Classique garde uniquement les aides immédiates. Le mode Pro expose
 * les réglages réellement applicables par le capteur : ISO, temps de pose,
 * focus manuel, balance des blancs, compensation d'exposition et zoom.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun CameraScreen(
    onPhotoSaved: (Uri) -> Unit,
    gpsFix: GpsFixState = GpsFixState.Searching,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            isTapToFocusEnabled = false
        }
    }

    var mode by remember { mutableStateOf(CameraMode.CLASSIC) }
    var flash by remember { mutableStateOf(FlashSetting.OFF) }
    var torchEnabled by remember { mutableStateOf(false) }
    var gridEnabled by remember { mutableStateOf(false) }
    var macroEnabled by remember { mutableStateOf(false) }
    var selectedProParameter by remember { mutableStateOf(ProParameter.ISO) }
    var proSettings by remember { mutableStateOf(ProSettings()) }
    var capabilities by remember { mutableStateOf(ProCapabilities()) }

    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var cameraReady by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }

    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        val zoomObserver = Observer<ZoomState?> { state ->
            if (state != null) {
                cameraReady = true
                minZoom = state.minZoomRatio
                maxZoom = state.maxZoomRatio
                zoomRatio = state.zoomRatio
                linearZoom = state.linearZoom
            }
        }
        controller.zoomState.observe(lifecycleOwner, zoomObserver)
        onDispose {
            controller.zoomState.removeObserver(zoomObserver)
            controller.unbind()
        }
    }

    LaunchedEffect(cameraReady) {
        if (!cameraReady) return@LaunchedEffect
        capabilities = readProCapabilities(controller)
        proSettings = proSettings.copy(
            iso = proSettings.iso.coerceIn(capabilities.isoRange),
            exposureTimeNanos = proSettings.exposureTimeNanos
                .coerceIn(capabilities.exposureTimeRangeNanos),
            focusDiopters = proSettings.focusDiopters
                .coerceIn(0f, capabilities.maxFocusDiopters),
            exposureCompensationIndex = proSettings.exposureCompensationIndex
                .coerceIn(capabilities.exposureCompensationRange),
        )
    }

    LaunchedEffect(mode, macroEnabled, proSettings, capabilities, cameraReady) {
        if (!cameraReady) return@LaunchedEffect
        applyCameraSettings(
            controller = controller,
            mode = mode,
            macroEnabled = macroEnabled,
            settings = proSettings,
            capabilities = capabilities,
        )
    }

    LaunchedEffect(mode) {
        controller.imageCaptureMode = if (mode == CameraMode.PRO) {
            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
        } else {
            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }

    LaunchedEffect(flash) {
        controller.imageCaptureFlashMode = flash.captureMode
    }

    LaunchedEffect(torchEnabled, cameraReady) {
        if (cameraReady) {
            runCatching { controller.enableTorch(torchEnabled) }
                .onFailure { Log.w(TAG, "Torche non appliquée", it) }
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(900)
            focusPoint = null
        }
    }

    var isCapturing by remember { mutableStateOf(false) }
    val onShutter = {
        if (!isCapturing) {
            isCapturing = true
            Haptics.tap(haptic)
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
                        "La photo n'a pas pu être prise",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    val currentShutter = rememberUpdatedState(onShutter)
    val activity = context.findActivity() as? MainActivity
    DisposableEffect(activity) {
        activity?.setVolumeCaptureHandler { currentShutter.value() }
        onDispose { activity?.setVolumeCaptureHandler(null) }
    }

    val currentMode = rememberUpdatedState(mode)
    val currentMacro = rememberUpdatedState(macroEnabled)
    val currentManualFocus = rememberUpdatedState(proSettings.manualFocus)
    val previewView = remember {
        PreviewView(context).apply {
            this.controller = controller
            val tapDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(event: MotionEvent): Boolean {
                        val tapFocusEnabled = currentMode.value == CameraMode.CLASSIC ||
                            !currentManualFocus.value
                        if (tapFocusEnabled) {
                            tapToFocus(
                                controller = controller,
                                previewView = this@apply,
                                x = event.x,
                                y = event.y,
                                macroEnabled = currentMode.value == CameraMode.CLASSIC &&
                                    currentMacro.value,
                            )
                            focusPoint = Offset(event.x, event.y)
                        }
                        return false
                    }
                },
            )
            setOnTouchListener { _, event ->
                tapDetector.onTouchEvent(event)
                false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CameraInk)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        CameraVignette()

        if (gridEnabled) {
            CompositionGridOverlay(modifier = Modifier.fillMaxSize())
        }

        focusPoint?.let {
            FocusReticleOverlay(
                point = it,
                proMode = mode == CameraMode.PRO,
                modifier = Modifier.fillMaxSize(),
            )
        }

        CameraTopBar(
            gpsFix = gpsFix,
            mode = mode,
            flash = flash,
            torchEnabled = torchEnabled,
            gridEnabled = gridEnabled,
            macroEnabled = macroEnabled,
            onFlash = { flash = flash.next() },
            onTorch = { torchEnabled = !torchEnabled },
            onGrid = { gridEnabled = !gridEnabled },
            onMacro = { macroEnabled = !macroEnabled },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )

        if (gpsFix is GpsFixState.Unavailable) {
            MissingLocationNotice(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (mode == CameraMode.PRO) 328.dp else 230.dp),
            )
        }

        CameraBottomControls(
            mode = mode,
            selectedProParameter = selectedProParameter,
            settings = proSettings,
            capabilities = capabilities,
            minZoom = minZoom,
            maxZoom = maxZoom,
            zoomRatio = zoomRatio,
            linearZoom = linearZoom,
            isCapturing = isCapturing,
            onModeChange = { nextMode ->
                mode = nextMode
                if (nextMode == CameraMode.PRO) {
                    macroEnabled = false
                    if (
                        !isProParameterEnabled(
                            selectedProParameter,
                            proSettings,
                            capabilities,
                        )
                    ) {
                        selectedProParameter = ProParameter.WHITE_BALANCE
                    }
                }
            },
            onSelectProParameter = { selectedProParameter = it },
            onSettingsChange = { proSettings = it },
            onLinearZoom = { value ->
                linearZoom = value
                controller.setLinearZoom(value)
            },
            onShutter = onShutter,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun CameraVignette() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to CameraInk.copy(alpha = 0.58f),
                    0.22f to Color.Transparent,
                    0.52f to Color.Transparent,
                    1f to CameraInk.copy(alpha = 0.96f),
                ),
            ),
    )
}

@Composable
private fun CameraTopBar(
    gpsFix: GpsFixState,
    mode: CameraMode,
    flash: FlashSetting,
    torchEnabled: Boolean,
    gridEnabled: Boolean,
    macroEnabled: Boolean,
    onFlash: () -> Unit,
    onTorch: () -> Unit,
    onGrid: () -> Unit,
    onMacro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GpsFixIndicator(gpsFix)

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraIconButton(
                icon = R.drawable.ic_camera_flash,
                contentDescription = "Flash : ${flash.name.lowercase()}",
                active = flash != FlashSetting.OFF,
                badge = flash.badge,
                proMode = mode == CameraMode.PRO,
                onClick = onFlash,
            )
            CameraIconButton(
                icon = R.drawable.ic_camera_torch,
                contentDescription = if (torchEnabled) {
                    "Éteindre la torche"
                } else {
                    "Allumer la torche"
                },
                active = torchEnabled,
                proMode = mode == CameraMode.PRO,
                onClick = onTorch,
            )
            CameraIconButton(
                icon = R.drawable.ic_camera_grid,
                contentDescription = if (gridEnabled) {
                    "Masquer la grille"
                } else {
                    "Afficher la grille"
                },
                active = gridEnabled,
                proMode = mode == CameraMode.PRO,
                onClick = onGrid,
            )
            if (mode == CameraMode.CLASSIC) {
                CameraIconButton(
                    icon = R.drawable.ic_camera_macro,
                    contentDescription = if (macroEnabled) {
                        "Désactiver le mode macro"
                    } else {
                        "Activer le mode macro"
                    },
                    active = macroEnabled,
                    proMode = false,
                    onClick = onMacro,
                )
            }
        }
    }
}

@Composable
private fun CameraIconButton(
    icon: Int,
    contentDescription: String,
    active: Boolean,
    proMode: Boolean,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val accent = if (proMode) CameraAmber else CameraGreen
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (active) accent.copy(alpha = 0.22f) else CameraGlass)
                .then(
                    if (active) {
                        Modifier.border(1.dp, accent.copy(alpha = 0.62f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = contentDescription,
                tint = if (active) accent else CameraWhite,
                modifier = Modifier.size(21.dp),
            )
        }
        badge?.let {
            Text(
                text = it,
                color = accent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(CameraInk)
                    .border(1.dp, accent.copy(alpha = 0.75f), CircleShape),
            )
        }
    }
}

@Composable
private fun GpsFixIndicator(
    state: GpsFixState,
    modifier: Modifier = Modifier,
) {
    val (label, color) = when (state) {
        GpsFixState.Searching -> "…" to CameraMuted
        is GpsFixState.Fixed -> "±${state.point.accuracyMeters.toInt()} m" to CameraGreen
        GpsFixState.Unavailable -> "—" to CameraDanger
    }
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(CameraGlass)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_camera_location),
            contentDescription = "État de la localisation",
            tint = color,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MissingLocationNotice(modifier: Modifier = Modifier) {
    Text(
        text = "GPS indisponible · photo non localisée",
        color = CameraWhite,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CameraGlass)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    )
}

@Composable
private fun CameraBottomControls(
    mode: CameraMode,
    selectedProParameter: ProParameter,
    settings: ProSettings,
    capabilities: ProCapabilities,
    minZoom: Float,
    maxZoom: Float,
    zoomRatio: Float,
    linearZoom: Float,
    isCapturing: Boolean,
    onModeChange: (CameraMode) -> Unit,
    onSelectProParameter: (ProParameter) -> Unit,
    onSettingsChange: (ProSettings) -> Unit,
    onLinearZoom: (Float) -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (mode == CameraMode.PRO) CameraAmber else CameraGreen
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (mode == CameraMode.PRO) {
            ProAdjustmentPanel(
                parameter = selectedProParameter,
                settings = settings,
                capabilities = capabilities,
                minZoom = minZoom,
                maxZoom = maxZoom,
                zoomRatio = zoomRatio,
                linearZoom = linearZoom,
                onSettingsChange = onSettingsChange,
                onLinearZoom = onLinearZoom,
            )
            Spacer(Modifier.height(6.dp))
            ProParameterStrip(
                selected = selectedProParameter,
                settings = settings,
                capabilities = capabilities,
                zoomRatio = zoomRatio,
                onSelect = onSelectProParameter,
            )
        } else if (maxZoom > minZoom) {
            ClassicZoomControl(
                minZoom = minZoom,
                maxZoom = maxZoom,
                zoomRatio = zoomRatio,
                linearZoom = linearZoom,
                onLinearZoom = onLinearZoom,
            )
        }

        Spacer(Modifier.height(if (mode == CameraMode.PRO) 8.dp else 12.dp))

        ShutterButton(
            isCapturing = isCapturing,
            accent = accent,
            onClick = onShutter,
        )

        Spacer(Modifier.height(10.dp))

        CameraModeSelector(
            selected = mode,
            onSelect = onModeChange,
        )
    }
}

@Composable
private fun ClassicZoomControl(
    minZoom: Float,
    maxZoom: Float,
    zoomRatio: Float,
    linearZoom: Float,
    onLinearZoom: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(CameraGlass)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatZoom(minZoom),
            color = CameraMuted,
            fontSize = 10.sp,
            modifier = Modifier.width(32.dp),
        )
        Slider(
            value = linearZoom.coerceIn(0f, 1f),
            onValueChange = onLinearZoom,
            colors = cameraSliderColors(CameraGreen),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatZoom(zoomRatio),
            color = CameraGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp),
        )
        Text(
            text = formatZoom(maxZoom),
            color = CameraMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun CameraModeSelector(
    selected: CameraMode,
    onSelect: (CameraMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(CameraGlass)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ModeItem(
            text = "CLASSIQUE",
            selected = selected == CameraMode.CLASSIC,
            accent = CameraGreen,
            onClick = { onSelect(CameraMode.CLASSIC) },
        )
        ModeItem(
            text = "PRO",
            selected = selected == CameraMode.PRO,
            accent = CameraAmber,
            onClick = { onSelect(CameraMode.PRO) },
        )
    }
}

@Composable
private fun ModeItem(
    text: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (selected) CameraInk else CameraMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 7.dp),
    )
}

@Composable
private fun ShutterButton(
    isCapturing: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(2.dp, CameraWhite, CircleShape)
            .padding(5.dp)
            .clip(CircleShape)
            .background(if (isCapturing) CameraMuted else CameraWhite)
            .semantics {
                contentDescription = if (isCapturing) {
                    "Photo en cours"
                } else {
                    "Prendre la photo"
                }
                role = Role.Button
            }
            .clickable(enabled = !isCapturing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(58.dp)) {
            drawCircle(
                color = accent.copy(alpha = if (isCapturing) 0.35f else 0.95f),
                radius = size.minDimension * 0.11f,
            )
            val tickWidth = 2.dp.toPx()
            val inner = size.minDimension * 0.31f
            val outer = size.minDimension * 0.42f
            listOf(0f, 90f, 180f, 270f).forEach { degrees ->
                val radians = Math.toRadians(degrees.toDouble())
                val cosine = kotlin.math.cos(radians).toFloat()
                val sine = kotlin.math.sin(radians).toFloat()
                drawLine(
                    color = accent,
                    start = center + Offset(cosine * inner, sine * inner),
                    end = center + Offset(cosine * outer, sine * outer),
                    strokeWidth = tickWidth,
                )
            }
        }
    }
}

@Composable
private fun ProParameterStrip(
    selected: ProParameter,
    settings: ProSettings,
    capabilities: ProCapabilities,
    zoomRatio: Float,
    onSelect: (ProParameter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CameraGlass)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ProParameter.entries.forEach { parameter ->
            val enabled = isProParameterEnabled(parameter, settings, capabilities)
            ParameterItem(
                parameter = parameter,
                value = proParameterValue(parameter, settings, capabilities, zoomRatio),
                selected = selected == parameter,
                enabled = enabled,
                onClick = { if (enabled) onSelect(parameter) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ParameterItem(
    parameter: ProParameter,
    value: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) CameraAmber.copy(alpha = 0.16f) else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = CameraAmber.copy(alpha = 0.48f),
                        shape = RoundedCornerShape(14.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = parameter.shortLabel,
            color = if (enabled) CameraMuted else CameraMuted.copy(alpha = 0.35f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            maxLines = 1,
        )
        Text(
            text = value,
            color = if (enabled) {
                if (selected) CameraAmber else CameraWhite
            } else {
                CameraMuted.copy(alpha = 0.35f)
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ProAdjustmentPanel(
    parameter: ProParameter,
    settings: ProSettings,
    capabilities: ProCapabilities,
    minZoom: Float,
    maxZoom: Float,
    zoomRatio: Float,
    linearZoom: Float,
    onSettingsChange: (ProSettings) -> Unit,
    onLinearZoom: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(CameraGlassLight)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        if (!isProParameterEnabled(parameter, settings, capabilities)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = parameter.shortLabel,
                    color = CameraAmber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp),
                )
                Text(
                    text = "Non pris en charge par ce capteur",
                    color = CameraMuted,
                    fontSize = 11.sp,
                )
            }
        } else {
            when (parameter) {
            ProParameter.ISO -> SliderAdjustment(
                title = "Sensibilité",
                value = settings.iso.toString(),
                sliderValue = logarithmicPosition(
                    settings.iso.toDouble(),
                    capabilities.isoRange.first.toDouble(),
                    capabilities.isoRange.last.toDouble(),
                ),
                onSliderValue = { position ->
                    onSettingsChange(
                        settings.copy(
                            manualExposure = true,
                            iso = logarithmicValue(
                                position,
                                capabilities.isoRange.first.toDouble(),
                                capabilities.isoRange.last.toDouble(),
                            ).roundToInt(),
                        ),
                    )
                },
                resetLabel = "A",
                onReset = { onSettingsChange(settings.copy(manualExposure = false)) },
            )

            ProParameter.SHUTTER -> SliderAdjustment(
                title = "Temps de pose",
                value = formatExposure(settings.exposureTimeNanos),
                sliderValue = logarithmicPosition(
                    settings.exposureTimeNanos.toDouble(),
                    capabilities.exposureTimeRangeNanos.first.toDouble(),
                    capabilities.exposureTimeRangeNanos.last.toDouble(),
                ),
                onSliderValue = { position ->
                    onSettingsChange(
                        settings.copy(
                            manualExposure = true,
                            exposureTimeNanos = logarithmicValue(
                                position,
                                capabilities.exposureTimeRangeNanos.first.toDouble(),
                                capabilities.exposureTimeRangeNanos.last.toDouble(),
                            ).toLong(),
                        ),
                    )
                },
                resetLabel = "A",
                onReset = { onSettingsChange(settings.copy(manualExposure = false)) },
            )

            ProParameter.FOCUS -> SliderAdjustment(
                title = "Mise au point",
                value = if (settings.manualFocus) {
                    formatFocus(settings.focusDiopters)
                } else {
                    "AF"
                },
                sliderValue = if (capabilities.maxFocusDiopters > 0f) {
                    settings.focusDiopters / capabilities.maxFocusDiopters
                } else {
                    0f
                },
                onSliderValue = { position ->
                    onSettingsChange(
                        settings.copy(
                            manualFocus = true,
                            focusDiopters = position * capabilities.maxFocusDiopters,
                        ),
                    )
                },
                resetLabel = "AF",
                onReset = { onSettingsChange(settings.copy(manualFocus = false)) },
            )

            ProParameter.WHITE_BALANCE -> WhiteBalanceAdjustment(
                selected = settings.whiteBalance,
                onSelect = { onSettingsChange(settings.copy(whiteBalance = it)) },
            )

            ProParameter.EXPOSURE -> {
                val range = capabilities.exposureCompensationRange
                val position = if (range.first == range.last) {
                    0.5f
                } else {
                    (settings.exposureCompensationIndex - range.first).toFloat() /
                        (range.last - range.first).toFloat()
                }
                SliderAdjustment(
                    title = "Exposition",
                    value = formatEv(
                        settings.exposureCompensationIndex,
                        capabilities.exposureCompensationStep,
                    ),
                    sliderValue = position,
                    onSliderValue = { sliderPosition ->
                        val index = (
                            range.first + sliderPosition * (range.last - range.first)
                            ).roundToInt()
                        onSettingsChange(
                            settings.copy(exposureCompensationIndex = index),
                        )
                    },
                    resetLabel = "0",
                    onReset = {
                        onSettingsChange(settings.copy(exposureCompensationIndex = 0))
                    },
                )
            }

            ProParameter.ZOOM -> SliderAdjustment(
                title = "Zoom",
                value = formatZoom(zoomRatio),
                sliderValue = linearZoom,
                onSliderValue = onLinearZoom,
                resetLabel = "1×",
                onReset = {
                    val oneX = if (maxZoom == minZoom) {
                        0f
                    } else {
                        ((1f - minZoom) / (maxZoom - minZoom)).coerceIn(0f, 1f)
                    }
                    onLinearZoom(oneX)
                },
            )
            }
        }
    }
}

@Composable
private fun SliderAdjustment(
    title: String,
    value: String,
    sliderValue: Float,
    onSliderValue: (Float) -> Unit,
    resetLabel: String,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.width(78.dp)) {
            Text(
                text = title,
                color = CameraMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = value,
                color = CameraAmber,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = onSliderValue,
            colors = cameraSliderColors(CameraAmber),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.dp, CameraAmber.copy(alpha = 0.55f), CircleShape)
                .clickable(onClick = onReset),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = resetLabel,
                color = CameraAmber,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WhiteBalanceAdjustment(
    selected: WhiteBalance,
    onSelect: (WhiteBalance) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "WB",
            color = CameraMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp),
        )
        WhiteBalance.entries.forEach { option ->
            Text(
                text = option.shortValue,
                color = if (selected == option) CameraInk else CameraMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (selected == option) CameraAmber else CameraInk.copy(alpha = 0.35f),
                    )
                    .clickable { onSelect(option) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun cameraSliderColors(accent: Color) = SliderDefaults.colors(
    thumbColor = accent,
    activeTrackColor = accent,
    inactiveTrackColor = CameraMuted.copy(alpha = 0.28f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent,
)

@Composable
private fun CompositionGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineColor = CameraWhite.copy(alpha = 0.32f)
        val strokeWidth = 0.8.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3f
            drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth)
        }
        for (i in 1..2) {
            val y = size.height * i / 3f
            drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth)
        }
    }
}

@Composable
private fun FocusReticleOverlay(
    point: Offset,
    proMode: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val color = if (proMode) CameraAmber else CameraGreen
        val radius = 25.dp.toPx()
        val gap = 9.dp.toPx()
        val tick = 8.dp.toPx()
        drawCircle(
            color = color.copy(alpha = 0.88f),
            radius = radius,
            center = point,
            style = Stroke(width = 1.2.dp.toPx()),
        )
        drawLine(color, point + Offset(0f, -radius - gap), point + Offset(0f, -radius + tick))
        drawLine(color, point + Offset(radius + gap, 0f), point + Offset(radius - tick, 0f))
        drawLine(color, point + Offset(0f, radius + gap), point + Offset(0f, radius - tick))
        drawLine(color, point + Offset(-radius - gap, 0f), point + Offset(-radius + tick, 0f))
    }
}

private fun proParameterValue(
    parameter: ProParameter,
    settings: ProSettings,
    capabilities: ProCapabilities,
    zoomRatio: Float,
): String = when (parameter) {
    ProParameter.ISO -> if (settings.manualExposure) settings.iso.toString() else "AUTO"
    ProParameter.SHUTTER -> if (settings.manualExposure) {
        formatExposure(settings.exposureTimeNanos)
    } else {
        "AUTO"
    }

    ProParameter.FOCUS -> if (settings.manualFocus) {
        formatFocus(settings.focusDiopters)
    } else {
        "AF"
    }

    ProParameter.WHITE_BALANCE -> settings.whiteBalance.shortValue
    ProParameter.EXPOSURE -> formatEv(
        settings.exposureCompensationIndex,
        capabilities.exposureCompensationStep,
    )

    ProParameter.ZOOM -> formatZoom(zoomRatio)
}

private fun isProParameterEnabled(
    parameter: ProParameter,
    settings: ProSettings,
    capabilities: ProCapabilities,
): Boolean = when (parameter) {
    ProParameter.ISO,
    ProParameter.SHUTTER,
    -> capabilities.manualSensor

    ProParameter.FOCUS -> capabilities.maxFocusDiopters > 0f
    ProParameter.EXPOSURE -> !settings.manualExposure &&
        capabilities.exposureCompensationRange.first !=
        capabilities.exposureCompensationRange.last

    ProParameter.WHITE_BALANCE,
    ProParameter.ZOOM,
    -> true
}

private fun formatZoom(value: Float): String =
    if (value >= 10f) {
        "${value.roundToInt()}×"
    } else {
        String.format(Locale.ROOT, "%.1f×", value)
    }

private fun formatExposure(nanos: Long): String {
    val seconds = nanos / 1_000_000_000.0
    return when {
        seconds >= 1.0 -> String.format(Locale.ROOT, "%.1fs", seconds).replace(".0s", "s")
        seconds <= 0.0 -> "—"
        else -> "1/${(1.0 / seconds).roundToInt()}"
    }
}

private fun formatFocus(diopters: Float): String {
    if (diopters <= 0.02f) return "∞"
    val centimeters = (100f / diopters).roundToInt()
    return if (centimeters >= 100) {
        String.format(Locale.ROOT, "%.1fm", centimeters / 100f)
    } else {
        "${centimeters}cm"
    }
}

private fun formatEv(index: Int, step: Float): String {
    val ev = index * step
    return when {
        step == 0f || index == 0 -> "0.0"
        ev > 0f -> String.format(Locale.ROOT, "+%.1f", ev)
        else -> String.format(Locale.ROOT, "%.1f", ev)
    }
}

private fun logarithmicPosition(value: Double, minimum: Double, maximum: Double): Float {
    if (minimum <= 0.0 || maximum <= minimum) return 0f
    return (ln(value.coerceIn(minimum, maximum) / minimum) / ln(maximum / minimum))
        .toFloat()
        .coerceIn(0f, 1f)
}

private fun logarithmicValue(position: Float, minimum: Double, maximum: Double): Double {
    if (minimum <= 0.0 || maximum <= minimum) return minimum
    return minimum * exp(ln(maximum / minimum) * position.coerceIn(0f, 1f))
}

@OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun readProCapabilities(controller: LifecycleCameraController): ProCapabilities {
    val cameraInfo = controller.cameraInfo ?: return ProCapabilities()
    return runCatching {
        val camera2Info = Camera2CameraInfo.from(cameraInfo)
        val availableCapabilities = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES,
        ) ?: intArrayOf()
        val manualSensor = availableCapabilities.contains(
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR,
        )
        val sensorIsoRange = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE,
        )
        val sensorExposureRange = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE,
        )
        val maxFocus = camera2Info.getCameraCharacteristic(
            CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE,
        ) ?: 0f
        val exposureState = cameraInfo.exposureState
        val exposureRange = exposureState.exposureCompensationRange

        ProCapabilities(
            manualSensor = manualSensor && sensorIsoRange != null && sensorExposureRange != null,
            isoRange = if (sensorIsoRange != null) {
                sensorIsoRange.lower..sensorIsoRange.upper
            } else {
                100..3200
            },
            exposureTimeRangeNanos = if (sensorExposureRange != null) {
                sensorExposureRange.lower..sensorExposureRange.upper
            } else {
                125_000L..1_000_000_000L
            },
            maxFocusDiopters = maxFocus.coerceAtLeast(0f),
            exposureCompensationRange = exposureRange.lower..exposureRange.upper,
            exposureCompensationStep = exposureState.exposureCompensationStep.toFloat(),
        )
    }.getOrElse {
        Log.w(TAG, "Capacités Pro indisponibles", it)
        ProCapabilities()
    }
}

@OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun applyCameraSettings(
    controller: LifecycleCameraController,
    mode: CameraMode,
    macroEnabled: Boolean,
    settings: ProSettings,
    capabilities: ProCapabilities,
) {
    val cameraControl = controller.cameraControl ?: return
    val options = CaptureRequestOptions.Builder()

    if (mode == CameraMode.CLASSIC) {
        options
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                if (macroEnabled) {
                    CaptureRequest.CONTROL_AF_MODE_MACRO
                } else {
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                },
            )
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON,
            )
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO,
            )
    } else {
        if (settings.manualFocus && capabilities.maxFocusDiopters > 0f) {
            options
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_OFF,
                )
                .setCaptureRequestOption(
                    CaptureRequest.LENS_FOCUS_DISTANCE,
                    settings.focusDiopters.coerceIn(0f, capabilities.maxFocusDiopters),
                )
        } else {
            options.setCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
            )
        }

        options.setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            settings.whiteBalance.camera2Mode,
        )

        if (settings.manualExposure && capabilities.manualSensor) {
            options
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF,
                )
                .setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    settings.iso.coerceIn(capabilities.isoRange),
                )
                .setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    settings.exposureTimeNanos.coerceIn(
                        capabilities.exposureTimeRangeNanos,
                    ),
                )
        } else {
            options.setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON,
            )
        }
    }

    runCatching {
        Camera2CameraControl.from(cameraControl).captureRequestOptions = options.build()
    }.onFailure { Log.w(TAG, "Réglages caméra non appliqués", it) }

    val exposureIndex = if (mode == CameraMode.PRO && !settings.manualExposure) {
        settings.exposureCompensationIndex.coerceIn(capabilities.exposureCompensationRange)
    } else {
        0
    }
    runCatching {
        cameraControl.setExposureCompensationIndex(exposureIndex)
    }.onFailure { Log.w(TAG, "Compensation d'exposition non appliquée", it) }
}

@OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun restoreFocusModeAfterTap(
    controller: LifecycleCameraController,
    macroEnabled: Boolean,
) {
    if (!macroEnabled) return
    val cameraControl = controller.cameraControl ?: return
    runCatching {
        Camera2CameraControl.from(cameraControl).captureRequestOptions =
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_MACRO,
                )
                .build()
    }.onFailure { Log.w(TAG, "Mode macro non restauré", it) }
}

private fun tapToFocus(
    controller: LifecycleCameraController,
    previewView: PreviewView,
    x: Float,
    y: Float,
    macroEnabled: Boolean,
) {
    val cameraControl = controller.cameraControl ?: return
    val point = previewView.meteringPointFactory.createPoint(x, y)
    val action = FocusMeteringAction.Builder(point).build()
    runCatching {
        val future = cameraControl.startFocusAndMetering(action)
        future.addListener(
            { restoreFocusModeAfterTap(controller, macroEnabled) },
            ContextCompat.getMainExecutor(previewView.context),
        )
    }.onFailure { Log.w(TAG, "Tap-to-focus non appliqué", it) }
}

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
                onSaved(results.savedUri ?: Uri.fromFile(photoFile))
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception)
            }
        },
    )
}
