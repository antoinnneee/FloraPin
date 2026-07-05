package com.florapin.app.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la logique PURE des actions rapides (TÂCHE 2.6) : quels boutons (❤️ /
 * Répondre) sont proposés selon le type de push et la présence d'une fleur.
 */
class NotificationQuickActionsTest {

    @Test
    fun `aucune action sans fleur`() {
        assertFalse(NotificationQuickActions.likeEnabled("flower_shared", null))
        assertFalse(NotificationQuickActions.replyEnabled("flower_shared", null))
        assertFalse(NotificationQuickActions.likeEnabled("flower_commented", ""))
        assertFalse(NotificationQuickActions.replyEnabled("flower_commented", ""))
    }

    @Test
    fun `un partage propose like et reponse`() {
        // Seul le partage est reçu par un NON-propriétaire : aimer une fleur
        // d'autrui a du sens, tout comme y répondre.
        assertTrue(NotificationQuickActions.likeEnabled("flower_shared", "flower-1"))
        assertTrue(NotificationQuickActions.replyEnabled("flower_shared", "flower-1"))
    }

    @Test
    fun `un commentaire recu propose repondre mais pas d'aimer sa propre fleur`() {
        // flower_commented n'est envoyé qu'au PROPRIÉTAIRE : aimer reviendrait à
        // aimer sa propre fleur. Répondre (commenter) reste pertinent.
        assertFalse(NotificationQuickActions.likeEnabled("flower_commented", "flower-1"))
        assertTrue(NotificationQuickActions.replyEnabled("flower_commented", "flower-1"))
    }

    @Test
    fun `un coeur recu ne propose pas d'aimer sa propre fleur`() {
        // « on a aimé VOTRE fleur » : aimer reviendrait à aimer sa propre fleur.
        assertFalse(NotificationQuickActions.likeEnabled("flower_liked", "flower-1"))
        // Répondre (commenter) reste pertinent.
        assertTrue(NotificationQuickActions.replyEnabled("flower_liked", "flower-1"))
    }

    @Test
    fun `types sans fleur actionnable n'ont aucune action`() {
        for (type in listOf("friend_request", "friend_accepted", "flower_shared_all", null)) {
            assertFalse(NotificationQuickActions.likeEnabled(type, "flower-1"))
            assertFalse(NotificationQuickActions.replyEnabled(type, "flower-1"))
        }
    }
}
