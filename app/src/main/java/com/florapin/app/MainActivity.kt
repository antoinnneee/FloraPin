package com.florapin.app

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.florapin.app.navigation.FloraNavHost
import com.florapin.app.push.NotificationRouting
import com.florapin.app.push.NotificationTarget
import com.florapin.app.ui.theme.FloraPinTheme
import com.florapin.app.update.UpdatePrompt
import com.florapin.app.update.UpdatePromptPreferences
import com.florapin.app.update.openPlayStore
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : ComponentActivity() {
    // Cible de navigation issue d'un tap sur notification. État observable pour
    // que le NavHost la consomme aussi bien au démarrage à froid (onCreate) que
    // lorsque l'app est déjà ouverte (onNewIntent, activité en singleTop).
    private val notificationTarget = mutableStateOf<NotificationTarget?>(null)
    private val availableUpdateVersion = mutableStateOf<Int?>(null)

    // Déclencheur de capture au volume : non nul uniquement quand l'écran de
    // capture est visible (il s'enregistre/désenregistre lui-même). Tant qu'il
    // reste nul, les touches de volume gardent leur comportement système normal.
    private var volumeCaptureHandler: (() -> Unit)? = null

    /**
     * (Dés)enregistre le déclencheur de capture au volume. Appelé par l'écran de
     * capture : une action à l'entrée, `null` à la sortie, pour n'intercepter les
     * touches de volume que lorsque l'aperçu caméra est affiché.
     */
    fun setVolumeCaptureHandler(handler: (() -> Unit)?) {
        volumeCaptureHandler = handler
    }

    // Interception des touches de volume (haut/bas) comme obturateur, mais
    // seulement si l'écran de capture a enregistré un déclencheur. `repeatCount`
    // == 0 évite les rafales quand l'utilisateur maintient la touche appuyée.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val handler = volumeCaptureHandler
        if (handler != null && isVolumeKey(keyCode)) {
            if (event == null || event.repeatCount == 0) handler()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // Consomme aussi le relâchement des touches de volume tant que le
    // déclencheur est actif, pour empêcher l'IHM système de volume d'apparaître.
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeCaptureHandler != null && isVolumeKey(keyCode)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationTarget.value = consumeNotificationTarget()
        checkForAvailableUpdate()
        setContent {
            FloraPinTheme {
                // Pas de Scaffold ici : chaque écran gère ses propres insets via
                // son TopAppBar/Scaffold. Un Scaffold racine en plus appliquerait
                // l'inset de la status bar une seconde fois (grand vide en haut).
                FloraNavHost(
                    modifier = Modifier.fillMaxSize(),
                    notificationTarget = notificationTarget.value,
                    onNotificationTargetHandled = { notificationTarget.value = null },
                )
                availableUpdateVersion.value?.let { versionCode ->
                    UpdatePrompt(
                        onDismiss = { doNotShowAgain ->
                            hideUpdatePrompt(versionCode, doNotShowAgain)
                        },
                        onOpenPlayStore = { doNotShowAgain ->
                            hideUpdatePrompt(versionCode, doNotShowAgain)
                            openPlayStore(this@MainActivity)
                        },
                    )
                }
            }
        }
    }

    private fun checkForAvailableUpdate() {
        val preferences = UpdatePromptPreferences(this)

        AppUpdateManagerFactory.create(this).appUpdateInfo
            .addOnSuccessListener { updateInfo ->
                if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    val versionCode = updateInfo.availableVersionCode()
                    if (preferences.shouldShow(versionCode)) {
                        availableUpdateVersion.value = versionCode
                    }
                }
            }
    }

    private fun hideUpdatePrompt(versionCode: Int, doNotShowAgain: Boolean) {
        if (doNotShowAgain) {
            UpdatePromptPreferences(this).dismiss(versionCode)
        }
        availableUpdateVersion.value = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop : l'activité est réutilisée. On adopte le nouvel intent puis
        // on relit ses extras pour router vers le contenu tapé.
        setIntent(intent)
        notificationTarget.value = consumeNotificationTarget()
    }

    /**
     * Lit la cible depuis l'intent courant PUIS retire ses extras : évite qu'une
     * recréation de l'activité (rotation, changement de config) ne re-route vers
     * le même contenu.
     */
    private fun consumeNotificationTarget(): NotificationTarget? {
        val target = NotificationRouting.parse(intent)
        intent?.removeExtra(NotificationRouting.EXTRA_TYPE)
        intent?.removeExtra(NotificationRouting.EXTRA_FLOWER_ID)
        intent?.removeExtra(NotificationRouting.EXTRA_GROUP_ID)
        return target
    }
}
