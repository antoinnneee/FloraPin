package com.florapin.app.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.florapin.app.ui.components.rememberSingleLineKeyboardActions
import com.florapin.app.ui.components.singleLineKeyboardOptions
import com.florapin.app.ui.theme.FloraPinTheme

/**
 * Écran de connexion (NODE-47). Sans état métier : reçoit l'état d'UI et émet
 * les intentions ; l'AuthViewModel (NODE-48) s'y branche.
 */
@Composable
fun LoginScreen(
    isLoading: Boolean,
    error: String?,
    onLogin: (email: String, password: String) -> Unit,
    onSwitchToRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val canSubmit = email.isNotBlank() && password.isNotBlank() && !isLoading

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Connexion", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = singleLineKeyboardOptions(KeyboardType.Email),
            keyboardActions = rememberSingleLineKeyboardActions(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe") },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.semantics {
                        contentDescription = if (passwordVisible) {
                            "Masquer le mot de passe"
                        } else {
                            "Afficher le mot de passe"
                        }
                    },
                ) {
                    Text(text = if (passwordVisible) "🙈" else "👁")
                }
            },
            keyboardOptions = singleLineKeyboardOptions(KeyboardType.Password),
            keyboardActions = rememberSingleLineKeyboardActions(),
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
            onClick = { onLogin(email.trim(), password) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 8.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text("Se connecter")
        }

        TextButton(
            onClick = onSwitchToRegister,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pas de compte ? S'inscrire")
        }

        TextButton(
            onClick = onForgotPassword,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Mot de passe oublié ?")
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    FloraPinTheme {
        LoginScreen(
            isLoading = false,
            error = null,
            onLogin = { _, _ -> },
            onSwitchToRegister = {},
            onForgotPassword = {},
        )
    }
}
