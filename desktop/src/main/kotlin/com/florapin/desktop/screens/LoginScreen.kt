package com.florapin.desktop.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import com.florapin.desktop.app.AppModel

/**
 * Connexion au compte FloraPin.
 *
 * Le compagnon ne propose pas de créer un compte : celui-ci se crée sur mobile,
 * là où l'on prend les photos. Proposer une inscription ici mènerait à un
 * compte vide et sans usage.
 */
@Composable
fun LoginScreen(model: AppModel) {
    var email by remember { mutableStateOf(model.tokenStore.lastEmail().orEmpty()) }
    var password by remember { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }
    val passwordFocus = remember { FocusRequester() }
    val emailFocus = remember { FocusRequester() }

    val submit = {
        if (email.isNotBlank() && password.isNotBlank() && !model.signingIn) {
            model.signIn(email, password)
        }
    }

    // Le curseur se place d'emblée dans le champ utile : sur l'email s'il est
    // vide, sinon directement sur le mot de passe.
    LaunchedEffect(Unit) {
        if (email.isBlank()) emailFocus.requestFocus() else passwordFocus.requestFocus()
    }

    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Card(Modifier.width(420.dp)) {
            Column(
                Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("FloraPin", style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Compagnon Windows — vos photos, vos albums, votre carte.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Adresse email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(emailFocus),
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = if (revealed) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = { revealed = !revealed }) {
                            Icon(
                                imageVector = if (revealed) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (revealed) {
                                    "Masquer le mot de passe"
                                } else {
                                    "Afficher le mot de passe"
                                },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
                )

                model.signInError?.let { error ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = submit,
                    enabled = !model.signingIn && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    if (model.signingIn) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Connexion…")
                        }
                    } else {
                        Text("Se connecter")
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Pas encore de compte ? Créez-le depuis l'application mobile, " +
                        "puis retrouvez ici toutes vos photos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
