package com.florapin.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.florapin.app.ui.theme.FloraPinTheme

/**
 * Écran d'inscription (NODE-47) : email + mot de passe + nom affiché.
 * Le mot de passe doit faire au moins 8 caractères (aligné sur l'API).
 */
@Composable
fun RegisterScreen(
    isLoading: Boolean,
    error: String?,
    onRegister: (email: String, password: String, displayName: String, syncEnabled: Boolean) -> Unit,
    onSwitchToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var displayName by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Choix de la sauvegarde cloud, fait dès l'inscription (réglage par appareil,
    // modifiable ensuite dans Profil). Activé par défaut.
    var syncEnabled by rememberSaveable { mutableStateOf(true) }

    val passwordTooShort = password.isNotEmpty() && password.length < 8
    val canSubmit = email.isNotBlank() &&
        displayName.isNotBlank() &&
        password.length >= 8 &&
        !isLoading

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Inscription", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Nom affiché") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe (8+ caractères)") },
            singleLine = true,
            isError = passwordTooShort,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Synchronisation cloud",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Sauvegardez vos fleurs sur le serveur et retrouvez-les " +
                        "sur vos autres appareils. Désactivée, l'app reste 100 % " +
                        "locale. Modifiable à tout moment dans Profil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = syncEnabled,
                onCheckedChange = { syncEnabled = it },
                enabled = !isLoading,
            )
        }

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = { onRegister(email.trim(), password, displayName.trim(), syncEnabled) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text("Créer mon compte")
        }

        TextButton(
            onClick = onSwitchToLogin,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Déjà un compte ? Se connecter")
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    FloraPinTheme {
        RegisterScreen(
            isLoading = false,
            error = null,
            onRegister = { _, _, _, _ -> },
            onSwitchToLogin = {},
        )
    }
}
