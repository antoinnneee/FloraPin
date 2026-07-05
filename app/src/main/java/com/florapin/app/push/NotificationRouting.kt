package com.florapin.app.push

import android.content.Intent

/**
 * Cible de navigation reconstruite depuis un tap sur notification. [type] est le
 * type FCM (« flower_shared », « flower_commented »…) ; [flowerServerId] est
 * l'identifiant SERVEUR (UUID) de la fleur concernée quand il y en a une — jamais
 * l'id local Room, qui n'existe pas dans le payload.
 */
data class NotificationTarget(
    val type: String?,
    val flowerServerId: String?,
)

/**
 * Passerelle entre le service de push et l'UI : le [FloraMessagingService] range
 * le type + le serverId de la fleur dans les extras du [Intent] du PendingIntent ;
 * [MainActivity] les relit au tap pour router vers le contenu concerné.
 */
object NotificationRouting {
    const val EXTRA_TYPE = "florapin.notif.type"
    const val EXTRA_FLOWER_ID = "florapin.notif.flowerId"

    /**
     * Reconstruit la cible depuis les extras de l'intent, ou `null` s'ils sont
     * absents (lancement classique depuis l'icône, deep link, etc.).
     */
    fun parse(intent: Intent?): NotificationTarget? {
        intent ?: return null
        val type = intent.getStringExtra(EXTRA_TYPE)?.takeIf { it.isNotBlank() }
        // Le serveur sérialise un flowerId absent en chaîne vide (FCM n'accepte
        // que des String) : on la traite comme « pas de fleur ».
        val flowerId = intent.getStringExtra(EXTRA_FLOWER_ID)?.takeIf { it.isNotBlank() }
        if (type == null && flowerId == null) return null
        return NotificationTarget(type, flowerId)
    }
}
