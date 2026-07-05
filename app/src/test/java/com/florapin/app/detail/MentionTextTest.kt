package com.florapin.app.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionTextTest {

    private val alice = "11111111-1111-1111-1111-111111111111"
    private val bob = "22222222-2222-2222-2222-222222222222"

    @Test
    fun `encode enveloppe l'identifiant`() {
        assertEquals("@[$alice]", MentionText.encode(alice))
    }

    @Test
    fun `activeQuery detecte une saisie @ en cours en fin de texte`() {
        assertEquals("mar", MentionText.activeQuery("Bonjour @mar"))
        assertEquals("", MentionText.activeQuery("Bonjour @"))
    }

    @Test
    fun `activeQuery ignore un token deja encode ou une saisie terminee`() {
        // Espace après la requête : la mention n'est plus en cours.
        assertNull(MentionText.activeQuery("Bonjour @mar "))
        // Token encodé complet : ce n'est pas une saisie libre.
        assertNull(MentionText.activeQuery("Salut @[$alice]"))
        // Aucun @.
        assertNull(MentionText.activeQuery("Rien à signaler"))
    }

    @Test
    fun `insertMention remplace la requete par le token encode et une espace`() {
        assertEquals(
            "Bonjour @[$alice] ",
            MentionText.insertMention("Bonjour @mar", alice),
        )
    }

    @Test
    fun `insertMention ajoute en fin quand aucune requete active`() {
        assertEquals(
            "Bonjour @[$alice] ",
            MentionText.insertMention("Bonjour ", alice),
        )
    }

    @Test
    fun `render remplace les tokens par les noms courants`() {
        val names = mapOf(alice to "Alice", bob to "Bob")
        assertEquals(
            "Coucou @Alice et @Bob !",
            MentionText.render("Coucou @[$alice] et @[$bob] !", names),
        )
    }

    @Test
    fun `render retombe sur quelqu'un si l'id est inconnu`() {
        assertEquals(
            "Coucou @quelqu'un",
            MentionText.render("Coucou @[$alice]", emptyMap()),
        )
    }

    @Test
    fun `segments decoupe texte et mentions dans l'ordre`() {
        val names = mapOf(alice to "Alice")
        val segments = MentionText.segments("Hey @[$alice] ok", names)
        assertEquals(
            listOf(
                MentionText.Segment.Literal("Hey "),
                MentionText.Segment.Mention(alice, "@Alice"),
                MentionText.Segment.Literal(" ok"),
            ),
            segments,
        )
    }
}
