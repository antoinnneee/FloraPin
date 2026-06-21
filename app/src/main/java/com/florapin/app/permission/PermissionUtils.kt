package com.florapin.app.permission

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity

/** Remonte la chaîne de [ContextWrapper] jusqu'à l'Activity hôte. */
fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    error("findActivity() appelé hors d'une ComponentActivity")
}

/**
 * Ouvre la page « Détails de l'application » des réglages système — seul recours
 * quand une permission a été refusée définitivement ("Ne plus demander").
 */
fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
