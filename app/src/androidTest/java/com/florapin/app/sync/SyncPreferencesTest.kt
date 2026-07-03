package com.florapin.app.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration du défaut de sync ON → OFF : préserve l'état des installations
 * existantes lors d'une mise à jour ([SyncPreferences.migrateDefaultForExistingInstall]).
 */
@RunWith(AndroidJUnit4::class)
class SyncPreferencesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun reset() {
        // Repart d'un état vierge (aucun choix enregistré).
        context.getSharedPreferences("florapin_sync", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun freshInstall_withoutSession_staysDisabled() {
        val prefs = SyncPreferences(context)

        prefs.migrateDefaultForExistingInstall(hasExistingSession = false)

        assertFalse(prefs.isEnabled()) // nouveau défaut device-first
    }

    @Test
    fun existingInstall_withSession_preservesLegacyEnabled() {
        val prefs = SyncPreferences(context)

        prefs.migrateDefaultForExistingInstall(hasExistingSession = true)

        assertTrue(prefs.isEnabled()) // ancien défaut ON conservé après mise à jour
    }

    @Test
    fun explicitChoice_isNeverOverwritten() {
        val prefs = SyncPreferences(context)
        prefs.setEnabled(false) // l'utilisateur a explicitement désactivé

        prefs.migrateDefaultForExistingInstall(hasExistingSession = true)

        assertFalse(prefs.isEnabled()) // le choix explicite prime sur la migration
    }
}
