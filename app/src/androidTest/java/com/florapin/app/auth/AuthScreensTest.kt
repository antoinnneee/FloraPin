package com.florapin.app.auth

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tests UI Compose des écrans Login/Register (NODE-60). */
@RunWith(AndroidJUnit4::class)
class AuthScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun login_submitDisabledUntilFieldsFilled_thenCallsOnLogin() {
        var submitted: Pair<String, String>? = null
        compose.setContent {
            LoginScreen(
                isLoading = false,
                error = null,
                onLogin = { email, password -> submitted = email to password },
                onSwitchToRegister = {},
            )
        }

        compose.onNodeWithText("Se connecter").assertIsNotEnabled()

        compose.onNodeWithText("Email").performTextInput("a@b.c")
        compose.onNodeWithText("Mot de passe").performTextInput("secret")

        compose.onNodeWithText("Se connecter").assertIsEnabled()
        compose.onNodeWithText("Se connecter").performClick()

        assertEquals("a@b.c" to "secret", submitted)
    }

    @Test
    fun login_showsError() {
        compose.setContent {
            LoginScreen(
                isLoading = false,
                error = "Identifiants invalides.",
                onLogin = { _, _ -> },
                onSwitchToRegister = {},
            )
        }
        compose.onNodeWithText("Identifiants invalides.").assertExists()
    }

    @Test
    fun register_submitDisabledWhenPasswordTooShort() {
        compose.setContent {
            RegisterScreen(
                isLoading = false,
                error = null,
                onRegister = { _, _, _ -> },
                onSwitchToLogin = {},
            )
        }

        compose.onNodeWithText("Email").performTextInput("a@b.c")
        compose.onNodeWithText("Nom affiché").performTextInput("Alice")
        compose.onNodeWithText("Mot de passe (8+ caractères)").performTextInput("court")

        // < 8 caractères : soumission désactivée.
        compose.onNodeWithText("Créer mon compte").assertIsNotEnabled()
    }

    @Test
    fun register_submitEnabledAndCallsOnRegister() {
        var name: String? = null
        compose.setContent {
            RegisterScreen(
                isLoading = false,
                error = null,
                onRegister = { _, _, displayName -> name = displayName },
                onSwitchToLogin = {},
            )
        }

        compose.onNodeWithText("Email").performTextInput("a@b.c")
        compose.onNodeWithText("Nom affiché").performTextInput("Alice")
        compose.onNodeWithText("Mot de passe (8+ caractères)").performTextInput("longpass")

        compose.onNodeWithText("Créer mon compte").assertIsEnabled()
        compose.onNodeWithText("Créer mon compte").performClick()

        assertEquals("Alice", name)
    }
}
