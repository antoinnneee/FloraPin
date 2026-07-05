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

/**
 * Regroupement des notifications système (TÂCHE 2.4). Toute l'app pousse ses
 * notifications sous une clé de groupe stable — par FLEUR quand une fleur est
 * concernée (une même fleur = une même conversation : cœur, commentaire,
 * proposition… se regroupent), sinon par TYPE (demandes d'ami…). À chaque ajout,
 * on reposte le résumé du groupe (`setGroupSummary`) pour que le système collapse
 * les notifications d'une même fleur au lieu de les empiler.
 *
 * Les ids de notification sont DÉTERMINISTES : un nouveau push d'un même
 * (type, fleur) réutilise le même id et MET À JOUR la notification existante
 * plutôt que d'en empiler une nouvelle. Les préfixes garantissent qu'un id
 * d'enfant et un id de résumé ne se télescopent jamais.
 *
 * Logique pure (sans dépendance Android) pour rester testable en unit test.
 */
object NotificationGrouping {

    /**
     * Clé de groupe : par fleur si une fleur est concernée (regroupe toute la
     * « conversation » autour d'elle), sinon par type.
     */
    fun groupKey(type: String?, flowerId: String?): String =
        flowerId?.takeIf { it.isNotBlank() }?.let { "florapin.group.flower.$it" }
            ?: "florapin.group.type.${type?.takeIf { it.isNotBlank() } ?: "unknown"}"

    /**
     * Id stable de la notification enfant, par (type, fleur) : un re-push du même
     * couple met à jour la notification plutôt que d'empiler.
     */
    fun childId(type: String?, flowerId: String?): Int =
        "child|${type.orEmpty()}|${flowerId.orEmpty()}".hashCode()

    /** Id stable du résumé, par groupe ; jamais confondu avec un enfant (préfixe). */
    fun summaryId(type: String?, flowerId: String?): Int =
        "summary|${groupKey(type, flowerId)}".hashCode()
}
