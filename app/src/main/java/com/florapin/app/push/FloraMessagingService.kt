package com.florapin.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.florapin.app.MainActivity
import com.florapin.app.R
import com.florapin.app.sync.ImageCacher
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
        // URL de la miniature de la fleur (TÂCHE 2.1) : URL présignée de lecture à
        // longue durée (7 jours), donc utilisable telle quelle. Absente des anciens
        // payloads et des push sans fleur → notification sans photo.
        val imageUrl = data["thumbnailUrl"]?.takeIf { it.isNotBlank() }
        val title = message.notification?.title ?: titleFor(type)
        val body = message.notification?.body ?: bodyFor(type, byUserName, species)
        showNotification(title, body, type, flowerId, imageUrl)
    }

    private fun showNotification(
        title: String,
        body: String,
        type: String?,
        flowerId: String?,
        imageUrl: String?,
    ) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureChannels(manager)
        }
        val channelId = channelFor(type)
        // Regroupement (TÂCHE 2.4) : toutes les notifications d'une même fleur (ou
        // d'un même type quand il n'y a pas de fleur) partagent une clé de groupe,
        // pour que le système les collapse sous un résumé au lieu de les empiler.
        val groupKey = NotificationGrouping.groupKey(type, flowerId)
        // Id STABLE par (type, fleur) : un re-push du même couple met à jour la
        // notification existante plutôt que d'en empiler une nouvelle. Sert aussi
        // de requestCode pour que le PendingIntent reste propre à ce (type, fleur).
        val notificationId = NotificationGrouping.childId(type, flowerId)
        // Miniature de la fleur (TÂCHE 2.5) : téléchargement synchrone à timeout
        // court, best-effort. En cas d'échec / d'absence d'URL, on retombe sur une
        // notification classique sans image. onMessageReceived tourne hors du thread
        // principal → bloquer brièvement ici est acceptable.
        val bigPicture = imageUrl?.let { ImageCacher.downloadBitmap(it) }
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setContentIntent(contentIntent(notificationId, type, flowerId))
        // Actions rapides (TÂCHE 2.6) : ❤️ et/ou « Répondre » directement depuis la
        // notification, selon le type (cf. NotificationQuickActions).
        addQuickActions(builder, notificationId, type, flowerId)
        if (bigPicture != null) {
            builder
                // Vignette visible en mode replié.
                .setLargeIcon(bigPicture)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bigPicture)
                        // Masque la grande icône une fois la notification dépliée
                        // (sinon la photo apparaît en double).
                        .bigLargeIcon(null as android.graphics.Bitmap?),
                )
        }
        manager.notify(notificationId, builder.build())
        // Le résumé doit être (re)posté à CHAQUE ajout pour que le groupe reflète
        // l'état courant et se collapse dès la 2ᵉ notification.
        postGroupSummary(manager, channelId, groupKey, type, flowerId)
    }

    /**
     * (Re)poste la notification « résumé » du groupe. Reposté à chaque ajout : le
     * système l'utilise pour collapser les notifications d'une même fleur /
     * conversation. Son id est stable par groupe (distinct des enfants) afin de la
     * mettre à jour plutôt que d'en créer une nouvelle.
     */
    private fun postGroupSummary(
        manager: NotificationManager,
        channelId: String,
        groupKey: String,
        type: String?,
        flowerId: String?,
    ) {
        val summaryId = NotificationGrouping.summaryId(type, flowerId)
        val summary = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(summaryTitleFor(flowerId))
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setGroupSummary(true)
            // Le tap sur le résumé route comme une notification du groupe (même
            // fleur / même type) ; requestCode dédié pour ne pas recycler l'intent
            // d'un enfant.
            .setContentIntent(contentIntent(summaryId, type, flowerId))
            .build()
        manager.notify(summaryId, summary)
    }

    /** Titre du résumé : par fleur (conversation) ou générique par type. */
    private fun summaryTitleFor(flowerId: String?): String =
        if (flowerId != null) "Activité sur une fleur" else "FloraPin"

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

    /**
     * Attache les boutons d'action rapide à la notification enfant (jamais au
     * résumé). Le like et la réponse portent sur une fleur : sans [flowerId], aucun
     * bouton. Le choix des actions (❤️, Répondre) est délégué à la logique pure
     * [NotificationQuickActions].
     */
    private fun addQuickActions(
        builder: NotificationCompat.Builder,
        notificationId: Int,
        type: String?,
        flowerId: String?,
    ) {
        val fid = flowerId?.takeIf { it.isNotBlank() } ?: return
        if (NotificationQuickActions.likeEnabled(type, fid)) {
            builder.addAction(likeAction(notificationId, fid))
        }
        if (NotificationQuickActions.replyEnabled(type, fid)) {
            builder.addAction(replyAction(notificationId, fid))
        }
    }

    /** Bouton « ❤️ J'aime » : POST flowers/{id}/like via [NotificationActionReceiver]. */
    private fun likeAction(notificationId: Int, flowerId: String): NotificationCompat.Action {
        val pending = actionPendingIntent(
            NotificationActionReceiver.ACTION_LIKE,
            notificationId,
            flowerId,
            // Immuable : aucune donnée n'est ajoutée à l'intent au déclenchement.
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(R.mipmap.ic_launcher, "❤️ J'aime", pending).build()
    }

    /**
     * Bouton « Répondre » : ouvre un champ de saisie (RemoteInput) dont le texte est
     * relu par [NotificationActionReceiver] pour poster un commentaire. Le
     * PendingIntent doit être MUTABLE (Android 12+) pour que le système y injecte la
     * réponse saisie.
     */
    private fun replyAction(notificationId: Int, flowerId: String): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_REMOTE_INPUT)
            .setLabel("Votre réponse…")
            .build()
        val pending = actionPendingIntent(
            NotificationActionReceiver.ACTION_REPLY,
            notificationId,
            flowerId,
            PendingIntent.FLAG_MUTABLE,
        )
        return NotificationCompat.Action.Builder(R.mipmap.ic_launcher, "Répondre", pending)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()
    }

    /**
     * PendingIntent (broadcast) vers [NotificationActionReceiver] transportant
     * l'action, la fleur et l'id de notification (pour la retirer après coup). Le
     * requestCode est stable par (action, notification) : un re-push met à jour
     * l'intent existant plutôt que d'en accumuler.
     */
    private fun actionPendingIntent(
        action: String,
        notificationId: Int,
        flowerId: String,
        mutabilityFlag: Int,
    ): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            // Action d'intent DISTINCTE par type + notification : évite que deux
            // PendingIntent « équivalents » se recyclent l'un l'autre.
            this.action = "florapin.notif.$action.$notificationId"
            putExtra(NotificationActionReceiver.EXTRA_QUICK_ACTION, action)
            putExtra(NotificationRouting.EXTRA_FLOWER_ID, flowerId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            this,
            "$action|$notificationId".hashCode(),
            intent,
            mutabilityFlag or PendingIntent.FLAG_UPDATE_CURRENT,
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
