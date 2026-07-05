package com.florapin.app.sync

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Résultat de la dernière passe de [SyncWorker], pour l'afficher (TÂCHE 6.14). */
enum class SyncOutcome {
    /** Aucune passe encore observée (état initial). */
    IDLE,

    /** Une passe est en cours d'exécution. */
    RUNNING,

    /** La dernière passe s'est terminée sans erreur. */
    SUCCESS,

    /** La dernière passe a échoué (voir [SyncStatus.errorMessage]). */
    ERROR,
}

/** Instantané de l'état de synchronisation exposé à l'UI. */
data class SyncStatus(
    val outcome: SyncOutcome,
    /** Message d'erreur de la dernière passe échouée, ou null. */
    val errorMessage: String?,
)

/**
 * Persiste l'état de la dernière passe de [SyncWorker] (en cours / réussie /
 * échouée + message d'erreur), pour l'afficher dans l'onglet Configuration
 * (TÂCHE 6.14).
 *
 * Fichier de prefs dédié (`florapin_sync_status`) : n'interfère pas avec
 * `florapin_sync` (réglage `sync_enabled` + curseur `last_sync_at`), qu'on ne
 * doit jamais `.clear()`. L'horodatage de la dernière synchro réussie reste, lui,
 * lu via [PrefsLastSyncStore] (`last_sync_at`).
 */
class SyncStatusStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Marque le début d'une passe. */
    fun markRunning() = write(SyncOutcome.RUNNING, null)

    /** Marque une passe réussie. */
    fun markSuccess() = write(SyncOutcome.SUCCESS, null)

    /** Marque une passe en échec, avec un éventuel message. */
    fun markError(message: String?) = write(SyncOutcome.ERROR, message)

    /** Lit l'état courant (IDLE si aucune passe n'a encore été observée). */
    fun read(): SyncStatus {
        val outcome = prefs.getString(KEY_OUTCOME, null)
            ?.let { runCatching { SyncOutcome.valueOf(it) }.getOrNull() }
            ?: SyncOutcome.IDLE
        return SyncStatus(outcome, prefs.getString(KEY_ERROR, null))
    }

    /**
     * Flux réactif de l'état : émet la valeur courante puis à chaque écriture, pour
     * que l'onglet Configuration reflète en direct le passage RUNNING → SUCCESS/ERROR.
     */
    fun flow(): Flow<SyncStatus> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun write(outcome: SyncOutcome, message: String?) {
        prefs.edit()
            .putString(KEY_OUTCOME, outcome.name)
            .putString(KEY_ERROR, message)
            .apply()
    }

    private companion object {
        const val PREFS = "florapin_sync_status"
        const val KEY_OUTCOME = "outcome"
        const val KEY_ERROR = "error"
    }
}
