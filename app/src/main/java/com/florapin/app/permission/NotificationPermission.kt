package com.florapin.app.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Prefs de suivi des demandes de permission (une seule sollicitation). */
private const val PREFS_NAME = "florapin_permission_prompts"
private const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"

/**
 * Demande la permission POST_NOTIFICATIONS (obligatoire dès Android 13 pour que
 * les push FCM soient visibles — I11) à l'arrivée sur l'écran principal d'un
 * utilisateur connecté.
 *
 * Une seule sollicitation : si l'utilisateur refuse, on ne le harcèle pas — il
 * pourra toujours accorder la permission depuis les réglages système. No-op
 * avant l'API 33 (permission accordée d'office) ou si déjà accordée.
 */
@Composable
fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Refus : pas de relance (les push resteront silencieux). */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return@LaunchedEffect

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_NOTIFICATIONS_ASKED, false)) return@LaunchedEffect

        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ASKED, true).apply()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
