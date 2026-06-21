package com.florapin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.florapin.app.navigation.FloraNavHost
import com.florapin.app.ui.theme.FloraPinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloraPinTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FloraNavHost(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}
