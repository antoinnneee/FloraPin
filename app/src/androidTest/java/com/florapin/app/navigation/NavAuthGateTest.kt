package com.florapin.app.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.florapin.app.network.auth.EncryptedTokenStore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Garde d'authentification du graphe de navigation (NODE-60) : sans session,
 * l'app démarre sur l'écran de connexion.
 */
@RunWith(AndroidJUnit4::class)
class NavAuthGateTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun startsOnLogin_whenLoggedOut() {
        EncryptedTokenStore(ApplicationProvider.getApplicationContext()).clear()

        compose.setContent { FloraNavHost() }

        // L'en-tête « Connexion » de LoginScreen est la destination de départ.
        compose.onNodeWithText("Connexion").assertExists()
    }
}
