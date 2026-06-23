package com.florapin.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Écran de réinitialisation (NODE-116) : le token (reçu par email via deep link
 * `florapin.fr/reset?token=...`, ou saisi manuellement) + le nouveau mot de
 * passe. Succès ⇒ [onResetDone] (retour Login).
 */
@Composable
fun ResetPasswordScreen(
    initialToken: String,
    isLoading: Boolean,
    resetDone: Boolean,
    error: String?,
    onSubmit: (token: String, newPassword: String) -> Unit,
    onResetDone: () -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var token by rememberSaveable { mutableStateOf(initialToken) }
    var password by remember { mutableStateOf("") }

    // Quand le serveur confirme le reset, on revient à Login.
    val currentOnResetDone by rememberUpdatedState(onResetDone)
    androidx.compose.runtime.LaunchedEffect(resetDone) {
        if (resetDone) currentOnResetDone()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Nouveau mot de passe", style = MaterialTheme.typography.headlineMedium)

        // Le token est généralement prérempli depuis le lien ; éditable au cas où.
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Code reçu par email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Nouveau mot de passe (8 caractères min.)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = { onSubmit(token.trim(), password) },
            enabled = token.isNotBlank() && password.length >= 8 && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text("Réinitialiser")
        }

        TextButton(
            onClick = onBackToLogin,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Retour à la connexion")
        }
    }
}
