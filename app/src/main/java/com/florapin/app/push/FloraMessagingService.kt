package com.florapin.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.florapin.app.MainActivity
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
        val data = message.data
        val type = data["type"]
        // Champs « incarnés » enrichis à l'envoi (TÂCHE 2.1) : nom de l'émetteur et
        // espèce de la fleur. Absents des anciennes versions du backend → fallback
        // sur les textes génériques.
        val byUserName = data["byUserName"]?.takeIf { it.isNotBlank() }
        val species = data["species"]?.takeIf { it.isNotBlank() }
        // serverId (UUID) de la fleur concernée, quand le payload en référence une
        // (partage ciblé, cœur, commentaire, identification…). Absent des partages
        // 'all'/'album' → le tap retombera sur le feed / l'écran par type.
        val flowerId = data["flowerId"]?.takeIf { it.isNotBlank() }
        val title = message.notification?.title ?: titleFor(type)
        val body = message.notification?.body ?: bodyFor(type, byUserName, species)
        showNotification(title, body, type, flowerId)
    }

    private fun showNotification(
        title: String,
        body: String,
        type: String?,
        flowerId: String?,
    ) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureChannels(manager)
        }
        val channelId = channelFor(type)
        // Chaque notification doit garder son propre PendingIntent (routage
        // distinct) : on dérive un id unique et on le partage entre le
        // PendingIntent et le notify() pour éviter que deux notifications se
        // recyclent le même intent.
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(notificationId, type, flowerId))
            .build()
        manager.notify(notificationId, notification)
    }

    /**
     * Crée (idempotent) les canaux par type. Android ignore un `createNotificationChannel`
     * dont l'id existe déjà, donc on peut appeler cette méthode à chaque notification.
     *
     * ⚠️ Un canal ne peut plus être modifié après création (nom/importance figés côté
     * système) : toute évolution de comportement passe par un NOUVEL id, pas par une
     * retouche d'un id existant. L'ancien canal unique `florapin_default` est conservé
     * comme repli pour les types non catégorisés (partage) et les types inconnus.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private fun ensureChannels(manager: NotificationManager) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_LIKES, "Cœurs", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_COMMENTS, "Commentaires", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_FRIENDS, "Amis", NotificationManager.IMPORTANCE_DEFAULT),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_IDENTIFICATION, "Identification", NotificationManager.IMPORTANCE_DEFAULT),
        )
        // Repli : partages et types inconnus. Réutilise l'id historique pour ne pas
        // laisser un canal orphelin dans les réglages système des installations existantes.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DEFAULT, "Général", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Associe un type FCM à son canal ; repli sur [CHANNEL_DEFAULT] si non catégorisé. */
    private fun channelFor(type: String?): String = when (type) {
        "flower_liked" -> CHANNEL_LIKES
        "flower_commented" -> CHANNEL_COMMENTS
        "friend_request", "friend_accepted" -> CHANNEL_FRIENDS
        "species_proposed", "species_confirmed", "identification_requested" ->
            CHANNEL_IDENTIFICATION
        else -> CHANNEL_DEFAULT
    }

    /**
     * PendingIntent qui ouvre [MainActivity] (singleTop) en transportant le type
     * et le serverId de la fleur, relus au tap pour router vers le contenu.
     * FLAG_IMMUTABLE est requis (targetSdk 35).
     */
    private fun contentIntent(
        requestCode: Int,
        type: String?,
        flowerId: String?,
    ): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            // CLEAR_TOP + singleTop (manifest) : réutilise l'activité existante et
            // déclenche onNewIntent plutôt que d'empiler une seconde instance.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            type?.let { putExtra(NotificationRouting.EXTRA_TYPE, it) }
            flowerId?.let { putExtra(NotificationRouting.EXTRA_FLOWER_ID, it) }
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Titre par défaut selon le type de notification (fallback sans payload notif). */
    private fun titleFor(type: String?): String = when (type) {
        "flower_shared" -> "Nouvelle fleur partagée"
        "friend_request" -> "Demande d'ami"
        "friend_accepted" -> "Demande d'ami acceptée"
        "species_proposed" -> "Proposition d'espèce"
        "species_confirmed" -> "Espèce confirmée"
        "flower_commented" -> "Nouveau commentaire"
        "flower_liked" -> "Nouveau cœur"
        "identification_requested" -> "Aide à l'identification"
        else -> "FloraPin"
    }

    /**
     * Corps « incarné » de la notification : intègre le nom de l'émetteur et
     * l'espèce quand le backend les a fournis (TÂCHE 2.1), sinon retombe sur les
     * textes génériques (compatibilité avec les anciens payloads).
     */
    private fun bodyFor(type: String?, byUserName: String?, species: String?): String {
        // « votre Coquelicot » quand l'espèce est connue, sinon « votre fleur ».
        val theFlower = species?.let { "votre $it" } ?: "votre fleur"
        val aFlower = species ?: "une fleur"
        return when (type) {
            "flower_shared" ->
                byUserName?.let { "$it a partagé $aFlower avec vous." }
                    ?: "Un ami a partagé une fleur avec vous."
            "friend_request" ->
                byUserName?.let { "$it vous a envoyé une demande d'ami." }
                    ?: "Vous avez reçu une demande d'ami."
            "friend_accepted" ->
                byUserName?.let { "$it a accepté votre demande d'ami." }
                    ?: "Votre demande d'ami a été acceptée."
            "flower_commented" ->
                byUserName?.let { "$it a commenté $theFlower." }
                    ?: "Quelqu'un a commenté votre fleur."
            "flower_liked" ->
                byUserName?.let { "$it a aimé $theFlower." }
                    ?: "Quelqu'un a aimé votre fleur."
            "species_proposed" ->
                byUserName?.let { name ->
                    species?.let { "$name propose : $it." } ?: "$name propose une espèce."
                } ?: "Un ami propose une espèce pour votre fleur."
            "species_confirmed" ->
                byUserName?.let { name ->
                    species?.let { "$name a confirmé : $it." }
                        ?: "$name a confirmé votre proposition."
                } ?: species?.let { "Votre proposition « $it » a été confirmée." }
                    ?: "Votre proposition d'espèce a été confirmée."
            "identification_requested" ->
                byUserName?.let { "$it demande de l'aide pour identifier une fleur." }
                    ?: "Un ami demande de l'aide pour identifier une fleur."
            else -> "Ouvrez FloraPin pour en savoir plus."
        }
    }

    private companion object {
        // Repli historique : partages et types inconnus.
        const val CHANNEL_DEFAULT = "florapin_default"
        // Canaux par type (TÂCHE 2.3). Ids figés : ne jamais les réutiliser pour
        // un autre comportement — créer un nouvel id si besoin.
        const val CHANNEL_LIKES = "florapin_likes"
        const val CHANNEL_COMMENTS = "florapin_comments"
        const val CHANNEL_FRIENDS = "florapin_friends"
        const val CHANNEL_IDENTIFICATION = "florapin_identification"
    }
}
