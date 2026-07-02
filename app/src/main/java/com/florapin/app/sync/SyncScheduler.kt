package com.florapin.app.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Planification de la synchronisation via WorkManager. */
object SyncScheduler {

    private const val PERIODIC = "florapin-sync-periodic"
    private const val ONESHOT = "florapin-sync-now"

    /** Clé d'entrée : forcer la passe même si la sync automatique est désactivée. */
    const val KEY_FORCE = "force"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Sync périodique (toutes les ~6 h, quand le réseau est disponible). */
    fun schedulePeriodic(context: Context) {
        if (!SyncPreferences(context).isEnabled()) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Annule toute synchronisation planifiée (ex. à la déconnexion). */
    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(ONESHOT)
    }

    /**
     * Sync immédiate.
     *
     * Par défaut (contextes automatiques : login, retour réseau, après une
     * modification), no-op si la synchronisation automatique est désactivée.
     * Avec [force] = true (bouton « Tout synchroniser »), la passe s'exécute
     * quel que soit le réglage automatique — seule la connexion à un compte est
     * requise (vérifiée dans [SyncWorker]).
     */
    fun syncNow(context: Context, force: Boolean = false) {
        if (!force && !SyncPreferences(context).isEnabled()) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setInputData(workDataOf(KEY_FORCE to force))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONESHOT,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
