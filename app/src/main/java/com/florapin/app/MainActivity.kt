package com.florapin.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.florapin.app.permission.PermissionsScreen
import com.florapin.app.permission.rememberMultiplePermissionsState
import com.florapin.app.ui.theme.FloraPinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FloraPinTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PermissionsGate(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun PermissionsGate(modifier: Modifier = Modifier) {
    val (permissionsState, requestPermissions) = rememberMultiplePermissionsState()
    PermissionsScreen(
        state = permissionsState,
        onRequest = requestPermissions,
        modifier = modifier,
    )
}
