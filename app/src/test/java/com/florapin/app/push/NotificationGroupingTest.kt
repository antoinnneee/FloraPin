package com.florapin.app.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vérifie la logique de regroupement (TÂCHE 2.4) : clés de groupe par fleur /
 * type et surtout la STABILITÉ des ids (mise à jour au lieu d'empilement) ainsi
 * que l'absence de collision entre enfants et résumés.
 */
class NotificationGroupingTest {

    @Test
    fun `regroupe par fleur quel que soit le type`() {
        val like = NotificationGrouping.groupKey("flower_liked", "flower-1")
        val comment = NotificationGrouping.groupKey("flower_commented", "flower-1")
        // Cœur et commentaire d'une même fleur = même conversation.
        assertEquals(like, comment)
    }

    @Test
    fun `separe deux fleurs distinctes`() {
        assertNotEquals(
            NotificationGrouping.groupKey("flower_liked", "flower-1"),
            NotificationGrouping.groupKey("flower_liked", "flower-2"),
        )
    }

    @Test
    fun `sans fleur regroupe par type`() {
        val a = NotificationGrouping.groupKey("friend_request", null)
        val b = NotificationGrouping.groupKey("friend_request", "")
        assertEquals(a, b)
        assertNotEquals(
            NotificationGrouping.groupKey("friend_request", null),
            NotificationGrouping.groupKey("friend_accepted", null),
        )
    }

    @Test
    fun `id enfant stable pour un meme couple type fleur`() {
        assertEquals(
            NotificationGrouping.childId("flower_commented", "flower-1"),
            NotificationGrouping.childId("flower_commented", "flower-1"),
        )
    }

    @Test
    fun `id enfant distinct par type sur la meme fleur`() {
        assertNotEquals(
            NotificationGrouping.childId("flower_liked", "flower-1"),
            NotificationGrouping.childId("flower_commented", "flower-1"),
        )
    }

    @Test
    fun `id resume stable et partage par toute la conversation`() {
        // Le résumé est un par groupe : cœur et commentaire d'une même fleur
        // repostent le même résumé (mise à jour, pas empilement).
        assertEquals(
            NotificationGrouping.summaryId("flower_liked", "flower-1"),
            NotificationGrouping.summaryId("flower_commented", "flower-1"),
        )
    }

    @Test
    fun `id resume ne collisionne pas avec les enfants du groupe`() {
        val flowerId = "flower-1"
        val summary = NotificationGrouping.summaryId("flower_liked", flowerId)
        val children = listOf(
            "flower_liked", "flower_commented", "species_proposed", "identification_requested",
        ).map { NotificationGrouping.childId(it, flowerId) }
        assertTrue(children.none { it == summary })
    }
}
