package com.florapin.app.capture

import android.annotation.SuppressLint
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.florapin.app.MainActivity
import com.florapin.app.location.GpsFixState
import com.florapin.app.permission.findActivity
import com.florapin.app.util.Haptics
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG = "CameraScreen"

/**
 * Aperçu caméra plein écran + bouton d'obturateur. À la prise, la photo est
 * enregistrée dans le stockage privé de l'app et [onPhotoSaved] est appelé avec
 * son [Uri].
 *
 * Contrôles disponibles pendant la visée :
 * - **zoom** : pincement à deux doigts (actif par défaut) ou curseur, synchronisés ;
 * - **mise au point par tap** : un appui fait le point sur la zone touchée
 *   (respecte le mode macro actif) ;
 * - **mode macro** : bascule la mise au point rapprochée pour les sujets très proches ;
 * - **flash** : déclenchement du flash à la prise de vue (éteint par défaut) ;
 * - **torche** : éclairage LED continu pendant la visée (éteint par défaut).
 *
 * Un **indicateur de fix GPS** ([gpsFix]) est affiché en haut à gauche pour
 * prévenir *avant* la prise si aucune position n'est disponible (la photo sera
 * alors enregistrée sans localisation).
 *
 * Suppose que la permission CAMERA est déjà accordée (gérée en amont).
 */
// setOnTouchListener sur la PreviewView : on relaie l'événement sans le consommer
// (pincement-zoom natif préservé), donc pas de performClick à implémenter.
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
            // On pilote nous-mêmes la mise au point par tap (cf. PreviewView plus
            // bas) pour pouvoir respecter le mode macro ; on désactive donc le
            // tap-to-focus intégré du contrôleur qui repasserait en AF standard.
            isTapToFocusEnabled = false
        }
    }

    // État du zoom, alimenté en continu par le contrôleur (reflète aussi le
    // pincement à deux doigts géré nativement par PreviewView).
    var minZoom by remember { mutableStateOf(1f) }
    var maxZoom by remember { mutableStateOf(1f) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var linearZoom by remember { mutableStateOf(0f) }
    // Passe à vrai dès que la caméra est liée (donc cameraControl disponible).
    var cameraReady by remember { mutableStateOf(false) }

    // Lie/délie le contrôleur au cycle de vie de l'écran et observe le zoom.
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
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

    var isCapturing by remember { mutableStateOf(false) }
    var macroEnabled by remember { mutableStateOf(false) }
    // Flash au déclenchement (piloté à la prise via imageCaptureFlashMode) et
    // torche continue (LED allumée pendant la visée via enableTorch) : deux
    // contrôles distincts, indépendants l'un de l'autre.
    var flashEnabled by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    // Grille de composition (règle des tiers) superposée à l'aperçu : simple aide
    // au cadrage, éteinte par défaut, sans effet sur la caméra ni sur la photo.
    var gridEnabled by remember { mutableStateOf(false) }

    // (Ré)applique le mode de mise au point dès que la bascule change ou que la
    // caméra devient prête.
    androidx.compose.runtime.LaunchedEffect(macroEnabled, cameraReady) {
        if (cameraReady) applyMacroFocus(controller, macroEnabled)
    }

    // Mode de flash à la prise : ON si activé, OFF sinon. Modifie une simple
    // propriété du contrôleur, sans attendre que la caméra soit liée.
    androidx.compose.runtime.LaunchedEffect(flashEnabled) {
        controller.imageCaptureFlashMode =
            if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    // Torche continue : n'a d'effet qu'une fois la caméra liée (cameraControl
    // disponible). Best effort — ignoré si l'appareil n'a pas de LED.
    androidx.compose.runtime.LaunchedEffect(torchEnabled, cameraReady) {
        if (cameraReady) {
            runCatching { controller.enableTorch(torchEnabled) }
                .onFailure { Log.w(TAG, "Torche non appliquée", it) }
        }
    }

    // Action d'obturateur partagée entre le bouton et les touches de volume :
    // ignore les appels si une capture est déjà en cours.
    val onShutter = {
        if (!isCapturing) {
            isCapturing = true
            // Confirmation haptique au déclenchement de l'obturateur (QOL 6.15).
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
                        "Échec de la capture : ${exc.message}",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }
    }

    // Déclencheur au volume : on enregistre l'obturateur auprès de l'activité
    // uniquement tant que l'écran de capture est composé (donc visible), et on le
    // retire à la sortie. rememberUpdatedState garde une référence fraîche vers
    // l'action sans avoir à ré-enregistrer à chaque recomposition.
    val currentShutter = rememberUpdatedState(onShutter)
    val activity = context.findActivity() as? MainActivity
    androidx.compose.runtime.DisposableEffect(activity) {
        activity?.setVolumeCaptureHandler { currentShutter.value() }
        onDispose { activity?.setVolumeCaptureHandler(null) }
    }

    // Valeur courante du mode macro, lue au moment du tap (le listener n'est
    // installé qu'une fois sur la PreviewView).
    val currentMacro = rememberUpdatedState(macroEnabled)
    val previewView = remember {
        PreviewView(context).apply {
            this.controller = controller
            // Détecteur de tap simple : à la levée du doigt, on lance la mise au
            // point sur le point touché. On relaie tous les événements à la
            // PreviewView (return false) pour conserver le pincement-zoom natif.
            val tapDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        tapToFocus(controller, this@apply, e.x, e.y, currentMacro.value)
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

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        // Grille de composition (règle des tiers) superposée à l'aperçu, purement
        // visuelle : deux traits verticaux et deux horizontaux découpant le cadre
        // en neuf. N'intercepte aucun geste (dessin seul), donc le tap-to-focus et
        // le pincement-zoom de la PreviewView restent actifs.
        if (gridEnabled) {
            CompositionGridOverlay(modifier = Modifier.fillMaxSize())
        }

        // Indicateur de fix GPS en haut à gauche : renseigne l'utilisateur sur la
        // disponibilité de la position *avant* qu'il déclenche la prise.
        GpsFixIndicator(
            state = gpsFix,
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
        )

        // Bascules « macro », « flash » et « torche » en haut à droite,
        // au-dessus de la barre d'état.
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = macroEnabled,
                onClick = { macroEnabled = !macroEnabled },
                label = { Text(if (macroEnabled) "🌿 Macro activé" else "🌿 Macro") },
            )
            FilterChip(
                selected = flashEnabled,
                onClick = { flashEnabled = !flashEnabled },
                label = { Text(if (flashEnabled) "⚡ Flash on" else "⚡ Flash") },
            )
            FilterChip(
                selected = torchEnabled,
                onClick = { torchEnabled = !torchEnabled },
                label = { Text(if (torchEnabled) "🔦 Torche on" else "🔦 Torche") },
            )
            FilterChip(
                selected = gridEnabled,
                onClick = { gridEnabled = !gridEnabled },
                label = { Text(if (gridEnabled) "▦ Grille on" else "▦ Grille") },
            )
        }

        // Bloc de contrôles bas : curseur de zoom puis obturateur.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Évite que la barre de navigation système (mode 3 boutons sur
                // certains Xiaomi/MIUI) recouvre les contrôles.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Le curseur n'apparaît que si l'appareil offre une plage de zoom.
            if (maxZoom > minZoom) {
                ZoomControl(
                    zoomRatio = zoomRatio,
                    linearZoom = linearZoom,
                    onLinearZoom = { value ->
                        linearZoom = value
                        controller.setLinearZoom(value)
                    },
                )
            }

            // Avertissement explicite juste au-dessus de l'obturateur quand aucune
            // position n'est disponible : la prise reste possible, mais la fleur
            // sera enregistrée sans localisation.
            if (gpsFix is GpsFixState.Unavailable) {
                Text(
                    text = "⚠️ Position GPS indisponible — la photo sera enregistrée sans localisation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            Button(
                onClick = onShutter,
                enabled = !isCapturing,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier.size(80.dp),
            ) {
                Text(if (isCapturing) "…" else "📷")
            }
        }
    }
}

/** Curseur de zoom + étiquette du facteur courant (ex. « 2.0× »). */
@Composable
private fun ZoomControl(
    zoomRatio: Float,
    linearZoom: Float,
    onLinearZoom: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = String.format("%.1f×", zoomRatio),
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
        Slider(
            value = linearZoom.coerceIn(0f, 1f),
            onValueChange = onLinearZoom,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Pastille d'état du fix GPS affichée pendant la visée. Fond translucide sombre
 * pour rester lisible par-dessus l'aperçu caméra quelle que soit la scène.
 */
@Composable
private fun GpsFixIndicator(
    state: GpsFixState,
    modifier: Modifier = Modifier,
) {
    val label = when (state) {
        GpsFixState.Searching -> "📡 GPS…"
        is GpsFixState.Fixed -> "📍 GPS ±${state.point.accuracyMeters.toInt()} m"
        GpsFixState.Unavailable -> "⚠️ GPS indisponible"
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * Grille de composition « règle des tiers » : deux traits verticaux et deux
 * horizontaux aux tiers du cadre, dessinés en blanc translucide. Aide au cadrage
 * uniquement — aucun impact sur la caméra ni sur la photo capturée.
 */
@Composable
private fun CompositionGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val lineColor = Color.White.copy(alpha = 0.4f)
        val strokeWidth = 1.dp.toPx()
        // Traits verticaux aux 1/3 et 2/3 de la largeur.
        for (i in 1..2) {
            val x = size.width * i / 3f
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = strokeWidth,
            )
        }
        // Traits horizontaux aux 1/3 et 2/3 de la hauteur.
        for (i in 1..2) {
            val y = size.height * i / 3f
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = strokeWidth,
            )
        }
    }
}

/**
 * Active/désactive la mise au point macro via l'interop Camera2.
 *
 * En macro on force [CaptureRequest.CONTROL_AF_MODE_MACRO] (mise au point sur
 * sujet rapproché) ; sinon on revient à l'autofocus continu standard. Best
 * effort : si le contrôleur n'est pas encore prêt ou si l'appareil ne gère pas
 * le mode, la requête est simplement ignorée par CameraX.
 */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
private fun applyMacroFocus(controller: LifecycleCameraController, enabled: Boolean) {
    val cameraControl = controller.cameraControl ?: return
    val afMode = if (enabled) {
        CaptureRequest.CONTROL_AF_MODE_MACRO
    } else {
        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
    }
    runCatching {
        Camera2CameraControl.from(cameraControl).captureRequestOptions =
            CaptureRequestOptions.Builder()
                .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, afMode)
                .build()
    }.onFailure { Log.w(TAG, "Mode macro non appliqué", it) }
}

/**
 * Mise au point par tap sur l'aperçu : convertit les coordonnées **vue** du
 * point touché en [androidx.camera.core.MeteringPoint] via la fabrique de la
 * [PreviewView] (qui connaît la transformation vue → capteur, y compris zoom et
 * rognage), puis lance un cycle de mise au point/mesure sur cette zone.
 *
 * Respecte le mode macro actif : `startFocusAndMetering` repasse l'AF en mode
 * automatique standard, on ré-applique donc [applyMacroFocus] une fois le cycle
 * terminé si le macro est activé, pour ne pas sortir silencieusement du mode.
 * Best effort : ignoré si la caméra n'est pas encore liée.
 */
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
            {
                if (macroEnabled) applyMacroFocus(controller, true)
            },
            ContextCompat.getMainExecutor(previewView.context),
        )
    }.onFailure { Log.w(TAG, "Tap-to-focus non appliqué", it) }
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
