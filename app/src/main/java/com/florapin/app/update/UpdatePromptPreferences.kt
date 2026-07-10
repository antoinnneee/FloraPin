package com.florapin.app.update

import android.content.Context

/** Memorise la version Play Store que l'utilisateur ne souhaite plus voir. */
class UpdatePromptPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun shouldShow(availableVersionCode: Int): Boolean =
        preferences.getInt(KEY_DISMISSED_VERSION, NO_VERSION) != availableVersionCode

    fun dismiss(availableVersionCode: Int) {
        preferences.edit().putInt(KEY_DISMISSED_VERSION, availableVersionCode).apply()
    }

    private companion object {
        const val PREFS_NAME = "florapin_updates"
        const val KEY_DISMISSED_VERSION = "dismissed_version_code"
        const val NO_VERSION = -1
    }
}
