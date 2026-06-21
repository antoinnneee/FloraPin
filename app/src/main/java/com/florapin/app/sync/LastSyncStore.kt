package com.florapin.app.sync

import android.content.Context

/** Persiste l'horodatage serveur du dernier pull réussi (curseur de sync). */
interface LastSyncStore {
    fun get(): String?
    fun set(value: String)
}

class PrefsLastSyncStore(context: Context) : LastSyncStore {
    private val prefs =
        context.applicationContext.getSharedPreferences("florapin_sync", Context.MODE_PRIVATE)

    override fun get(): String? = prefs.getString(KEY, null)

    override fun set(value: String) {
        prefs.edit().putString(KEY, value).apply()
    }

    private companion object {
        const val KEY = "last_sync_at"
    }
}
