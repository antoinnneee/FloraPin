package com.florapin.app.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class ObservationNotesTest {

    @Test
    fun `une ancienne note devient la premiere note`() {
        assertEquals(
            listOf("Feuilles très parfumées."),
            decodeObservationNotes("Feuilles très parfumées."),
        )
    }

    @Test
    fun `plusieurs notes conservent leur contenu et leurs paragraphes`() {
        val notes = listOf(
            "Première floraison.\nDeux fleurs ouvertes.",
            "Arrosage après trois jours secs.",
            "Bouton observé sur la tige principale.",
        )

        assertEquals(notes, decodeObservationNotes(encodeObservationNotes(notes)))
    }

    @Test
    fun `les notes vides sont ignorees lors de la sauvegarde`() {
        assertEquals(
            listOf("Une note utile"),
            decodeObservationNotes(encodeObservationNotes(listOf("", "  ", "Une note utile"))),
        )
    }
}
