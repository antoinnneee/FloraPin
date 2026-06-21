package com.florapin.app.permission

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** État d'une permission du point de vue de l'UI. */
enum class PermissionStatus {
    /** Accordée. */
    GRANTED,

    /** Refusée, mais on peut encore la redemander. */
    DENIED,

    /** Refusée définitivement ("Ne plus demander") : passer par les réglages. */
    PERMANENTLY_DENIED,
}

val PermissionStatus.isGranted: Boolean
    get() = this == PermissionStatus.GRANTED

/**
 * Détient l'état d'un groupe de permissions et le réévalue à la demande.
 *
 * La distinction [PermissionStatus.PERMANENTLY_DENIED] n'est fiable qu'APRÈS une
 * première demande : tant qu'on n'a pas demandé, `shouldShowRequestPermissionRationale`
 * renvoie `false` aussi pour un état initial — d'où le suivi de [askedOnce].
 */
@Stable
class MultiplePermissionsState internal constructor(
    private val activity: ComponentActivity,
    val permissions: List<AppPermission>,
) {
    private val askedOnce = mutableSetOf<String>()

    var statuses: Map<AppPermission, PermissionStatus> by mutableStateOf(computeStatuses())
        private set

    /** Vrai si toutes les permissions du groupe sont accordées. */
    val allGranted: Boolean
        get() = statuses.values.all { it == PermissionStatus.GRANTED }

    /** Recalcule les statuts (ex. au retour depuis les réglages). */
    fun refresh() {
        statuses = computeStatuses()
    }

    internal fun onResult(result: Map<String, Boolean>) {
        askedOnce.addAll(result.keys)
        refresh()
    }

    private fun computeStatuses(): Map<AppPermission, PermissionStatus> =
        permissions.associateWith { permission ->
            val manifest = permission.manifestPermission
            when {
                isGranted(manifest) -> PermissionStatus.GRANTED
                manifest in askedOnce &&
                    !activity.shouldShowRequestPermissionRationale(manifest) ->
                    PermissionStatus.PERMANENTLY_DENIED

                else -> PermissionStatus.DENIED
            }
        }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) ==
            PackageManager.PERMISSION_GRANTED
}

/**
 * Crée et mémorise un [MultiplePermissionsState] et la lambda de demande associée.
 *
 * Les statuts sont automatiquement réévalués à chaque `ON_RESUME` afin de refléter
 * un changement fait dans les réglages système.
 *
 * @return le state, et une lambda à appeler pour lancer la demande système.
 */
@Composable
fun rememberMultiplePermissionsState(
    permissions: List<AppPermission> = AppPermission.poc,
): Pair<MultiplePermissionsState, () -> Unit> {
    val activity = LocalContext.current.findActivity()
    val state = remember(activity, permissions) {
        MultiplePermissionsState(activity, permissions)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> state.onResult(result) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val request: () -> Unit = {
        launcher.launch(permissions.map { it.manifestPermission }.toTypedArray())
    }
    return state to request
}
