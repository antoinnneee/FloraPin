package com.florapin.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.florapin.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Réception des push FCM : ré-enregistre le jeton quand il change et affiche une
 * notification système à la réception d'un message.
 */
class FloraMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Le jeton a changé : on le ré-enregistre côté serveur.
        PushTokenRegistrar.register(applicationContext)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data["type"]
        val title = message.notification?.title ?: titleFor(type)
        val body = message.notification?.body ?: bodyFor(type)
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "FloraPin",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    /** Titre par défaut selon le type de notification (fallback sans payload notif). */
    private fun titleFor(type: String?): String = when (type) {
        "flower_shared" -> "Nouvelle fleur partagée"
        "friend_request" -> "Demande d'ami"
        "friend_accepted" -> "Demande d'ami acceptée"
        "species_proposed" -> "Proposition d'espèce"
        "species_confirmed" -> "Espèce confirmée"
        else -> "FloraPin"
    }

    private fun bodyFor(type: String?): String = when (type) {
        "flower_shared" -> "Un ami a partagé une fleur avec vous."
        "friend_request" -> "Vous avez reçu une demande d'ami."
        "friend_accepted" -> "Votre demande d'ami a été acceptée."
        else -> "Ouvrez FloraPin pour en savoir plus."
    }

    private companion object {
        const val CHANNEL_ID = "florapin_default"
    }
}
