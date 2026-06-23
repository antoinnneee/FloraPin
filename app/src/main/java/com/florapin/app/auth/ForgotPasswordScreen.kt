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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Écran « mot de passe oublié » (NODE-116) : saisie de l'email. Réponse
 * volontairement neutre (anti-énumération) — on n'indique pas si le compte
 * existe.
 */
@Composable
fun ForgotPasswordScreen(
    isLoading: Boolean,
    requestSent: Boolean,
    error: String?,
    onSubmit: (email: String) -> Unit,
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Mot de passe oublié", style = MaterialTheme.typography.headlineMedium)

        if (requestSent) {
            Text(
                "Si un compte existe pour cette adresse, un lien de " +
                    "réinitialisation vient d'être envoyé. Vérifiez vos emails.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Retour à la connexion")
            }
            return@Column
        }

        Text(
            "Saisissez l'email de votre compte ; nous vous enverrons un lien " +
                "pour choisir un nouveau mot de passe.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
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
            onClick = { onSubmit(email.trim()) },
            enabled = email.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text("Envoyer le lien")
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
