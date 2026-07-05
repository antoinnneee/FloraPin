package com.florapin.app

import android.content.Intent
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    // Cible de navigation issue d'un tap sur notification. État observable pour
    // que le NavHost la consomme aussi bien au démarrage à froid (onCreate) que
    // lorsque l'app est déjà ouverte (onNewIntent, activité en singleTop).
    private val notificationTarget = mutableStateOf<NotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationTarget.value = consumeNotificationTarget()
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
            }
        }
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
        return target
    }
}
