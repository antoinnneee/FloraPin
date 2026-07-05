package com.florapin.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.florapin.app.network.NetworkModule
import com.florapin.app.network.auth.EncryptedTokenStore
import com.florapin.app.network.dto.CreateCommentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Exécute les actions rapides déclenchées depuis une notification (TÂCHE 2.6) :
 * « ❤️ J'aime » et « Répondre » (RemoteInput → commentaire), sans ouvrir l'app.
 *
 * L'appel réseau NE peut PAS bloquer `onReceive` (fenêtre de ~10 s, thread
 * principal) : on prolonge la vie du receiver avec [goAsync] puis on lance une
 * coroutine IO qui appelle le backend et n'appelle [finish][PendingResult.finish]
 * qu'une fois terminée. L'auth réutilise l'[EncryptedTokenStore] applicatif et le
 * client authentifié **partagé** ([NetworkModule.createAuthenticated]) — jamais un
 * second OkHttpClient.
 *
 * Best-effort : en cas d'échec (hors-ligne, non connecté, réponse vide…) on
 * journalise et on laisse la notification en place pour permettre un nouvel essai ;
 * en cas de succès on retire la notification traitée.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val flowerId = intent
            .getStringExtra(NotificationRouting.EXTRA_FLOWER_ID)
            ?.takeIf { it.isNotBlank() } ?: return
        val action = intent.getStringExtra(EXTRA_QUICK_ACTION) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        // Texte saisi dans le champ de réponse rapide (RemoteInput), le cas échéant.
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REMOTE_INPUT)
            ?.toString()
            ?.trim()

        val appContext = context.applicationContext
        val store = EncryptedTokenStore(appContext)
        // Non connecté : aucune action possible (ne devrait pas arriver depuis une
        // notification, mais on dégrade proprement).
        if (store.refreshToken() == null) return

        // Prolonge la vie du receiver le temps de l'appel réseau.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val succeeded = runCatching {
                val apis = NetworkModule.createAuthenticated(store)
                when (action) {
                    ACTION_LIKE -> apis.likes.like(flowerId).isSuccessful
                    ACTION_REPLY ->
                        if (replyText.isNullOrBlank()) {
                            false
                        } else {
                            apis.comments.post(flowerId, CreateCommentRequest(replyText))
                            true
                        }
                    else -> false
                }
            }.onFailure { Log.w(TAG, "action « $action » échouée", it) }
                .getOrDefault(false)

            // Succès : on retire la notification traitée (le contenu est à jour côté
            // serveur). Échec : on la laisse pour un nouvel essai.
            if (succeeded) {
                runCatching { NotificationManagerCompat.from(appContext).cancel(notificationId) }
            }
            pending.finish()
        }
    }

    companion object {
        private const val TAG = "NotifActionReceiver"

        /** Extra portant le type d'action rapide ([ACTION_LIKE] / [ACTION_REPLY]). */
        const val EXTRA_QUICK_ACTION = "florapin.notif.quickAction"

        /** Extra portant l'id de la notification à retirer une fois l'action jouée. */
        const val EXTRA_NOTIFICATION_ID = "florapin.notif.id"

        /** Clé du texte saisi dans le champ « Répondre » (RemoteInput). */
        const val KEY_REMOTE_INPUT = "florapin.notif.replyText"

        const val ACTION_LIKE = "like"
        const val ACTION_REPLY = "reply"
    }
}
