package com.florapin.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Écran de confirmation de vérification d'email (NODE-117), ouvert via le deep
 * link `florapin.fr/verify?token=...`. Valide automatiquement le token reçu.
 */
@Composable
fun VerifyEmailScreen(
    token: String,
    state: EmailVerifyUiState,
    onVerify: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(token) { onVerify(token) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.loading -> {
                CircularProgressIndicator()
                Text("Vérification en cours…", style = MaterialTheme.typography.bodyMedium)
            }

            state.verified -> {
                Text("✓", style = MaterialTheme.typography.displayMedium)
                Text(
                    "Votre adresse email est vérifiée.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuer")
                }
            }

            else -> {
                Text(
                    state.error ?: "Vérification impossible.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuer")
                }
            }
        }
    }
}
