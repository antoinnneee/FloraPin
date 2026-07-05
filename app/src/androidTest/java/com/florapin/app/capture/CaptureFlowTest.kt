package com.florapin.app.capture

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.florapin.app.location.GeoPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests UI Compose du flux de capture (NODE-6/7), côté écran de **revue** de la
 * photo. On pilote directement [CapturedPhotoScreen] avec une source d'image
 * factice (URI bidon) : l'aperçu CameraX n'est pas instrumentable sur émulateur,
 * conformément à la contrainte de la tâche 1.8.
 */
@RunWith(AndroidJUnit4::class)
class CaptureFlowTest {

    @get:Rule
    val compose = createComposeRule()

    // URI factice : l'image ne se charge pas (Coil échoue silencieusement), mais
    // l'écran et ses actions se composent normalement — c'est tout ce qu'on teste.
    private val fakeUri: Uri = Uri.parse("file:///dev/null/fake.jpg")

    @Test
    fun cover_showsGroupCount_andActions() {
        compose.setContent {
            CapturedPhotoScreen(
                captured = Captured.Cover(fakeUri),
                photoCount = 1,
                locationState = LocationState.Available(GeoPoint(48.8566, 2.3522, 5f)),
                onCancelPhoto = {},
                onAddAnother = {},
                onFinish = {},
            )
        }

        compose.onNodeWithText("📸 1 photo dans ce groupe").assertIsDisplayed()
        compose.onNodeWithText("Terminer").assertIsDisplayed()
        compose.onNodeWithText("➕ Ajouter une photo").assertIsDisplayed()
        // Sur une couverture, l'annulation efface toute la capture.
        compose.onNodeWithText("Annuler cette capture").assertIsDisplayed()
    }

    @Test
    fun cover_pluralizesCount() {
        compose.setContent {
            CapturedPhotoScreen(
                captured = Captured.Cover(fakeUri),
                photoCount = 3,
                locationState = LocationState.Loading,
                onCancelPhoto = {},
                onAddAnother = {},
                onFinish = {},
            )
        }

        compose.onNodeWithText("📸 3 photos dans ce groupe").assertIsDisplayed()
    }

    @Test
    fun addedPhoto_showsSinglePhotoCancelLabel() {
        compose.setContent {
            CapturedPhotoScreen(
                captured = Captured.Added(fakeUri, photoId = 42L),
                photoCount = 2,
                locationState = LocationState.Unavailable,
                onCancelPhoto = {},
                onAddAnother = {},
                onFinish = {},
            )
        }

        // Une photo additionnelle : l'annulation ne retire que cette photo.
        compose.onNodeWithText("Annuler cette photo").assertIsDisplayed()
        compose.onNodeWithText("Annuler cette capture").assertDoesNotExist()
    }

    @Test
    fun locationLine_reflectsState() {
        compose.setContent {
            CapturedPhotoScreen(
                captured = Captured.Cover(fakeUri),
                photoCount = 1,
                locationState = LocationState.Unavailable,
                onCancelPhoto = {},
                onAddAnother = {},
                onFinish = {},
            )
        }

        compose.onNodeWithText("📍 Localisation indisponible").assertIsDisplayed()
    }

    @Test
    fun actions_invokeCallbacks() {
        var finished = false
        var added = false
        var canceled = false
        compose.setContent {
            CapturedPhotoScreen(
                captured = Captured.Cover(fakeUri),
                photoCount = 1,
                locationState = LocationState.Loading,
                onCancelPhoto = { canceled = true },
                onAddAnother = { added = true },
                onFinish = { finished = true },
            )
        }

        compose.onNodeWithText("➕ Ajouter une photo").performClick()
        assertTrue(added)
        assertFalse(finished)
        assertFalse(canceled)

        compose.onNodeWithText("Terminer").performClick()
        assertTrue(finished)

        compose.onNodeWithText("Annuler cette capture").performClick()
        assertTrue(canceled)
    }
}
