package com.florapin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.florapin.app.navigation.FloraNavHost
import com.florapin.app.ui.theme.FloraPinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloraPinTheme {
                // Pas de Scaffold ici : chaque écran gère ses propres insets via
                // son TopAppBar/Scaffold. Un Scaffold racine en plus appliquerait
                // l'inset de la status bar une seconde fois (grand vide en haut).
                FloraNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
