package com.florapin.desktop.app

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/**
 * La sélection multiple est la mécanique la plus « musculaire » de l'interface :
 * l'utilisateur l'exécute sans y penser, donc le moindre écart avec les
 * conventions de l'Explorateur Windows se remarque immédiatement.
 */
class SelectionTest {

    private val items = listOf("a", "b", "c", "d", "e")

    @Test
    fun `un clic simple ne garde que l'element clique`() {
        val selection = Selection()
        selection.click("a", items, ctrl = false, shift = false)
        selection.click("c", items, ctrl = false, shift = false)

        assertEquals(setOf("c"), selection.ids)
    }

    @Test
    fun `ctrl-clic ajoute puis retire sans toucher au reste`() {
        val selection = Selection()
        selection.click("a", items, ctrl = false, shift = false)
        selection.click("c", items, ctrl = true, shift = false)
        assertEquals(setOf("a", "c"), selection.ids)

        selection.click("a", items, ctrl = true, shift = false)
        assertEquals(setOf("c"), selection.ids)
    }

    @Test
    fun `maj-clic selectionne la plage depuis l'ancre`() {
        val selection = Selection()
        selection.click("b", items, ctrl = false, shift = false)
        selection.click("d", items, ctrl = false, shift = true)

        assertEquals(setOf("b", "c", "d"), selection.ids)
    }

    @Test
    fun `maj-clic fonctionne vers le haut`() {
        val selection = Selection()
        selection.click("d", items, ctrl = false, shift = false)
        selection.click("b", items, ctrl = false, shift = true)

        assertEquals(setOf("b", "c", "d"), selection.ids)
    }

    @Test
    fun `deux maj-clics successifs repartent de la meme ancre`() {
        val selection = Selection()
        selection.click("b", items, ctrl = false, shift = false)
        selection.click("e", items, ctrl = false, shift = true)
        // Rétrécir la plage doit fonctionner, pas seulement l'élargir : c'est
        // ce que permet une ancre stable.
        selection.click("c", items, ctrl = false, shift = true)

        assertEquals(setOf("b", "c"), selection.ids)
    }

    @Test
    fun `ctrl-maj-clic ajoute la plage a la selection existante`() {
        val selection = Selection()
        selection.click("e", items, ctrl = false, shift = false)
        selection.click("a", items, ctrl = true, shift = false)
        selection.click("c", items, ctrl = true, shift = true)

        assertEquals(setOf("a", "b", "c", "e"), selection.ids)
    }

    @Test
    fun `maj-clic sans ancre se comporte comme un clic simple`() {
        val selection = Selection()
        selection.click("c", items, ctrl = false, shift = true)

        assertEquals(setOf("c"), selection.ids)
    }

    @Test
    fun `selectAll prend tout ce qui est visible`() {
        val selection = Selection()
        selection.selectAll(items)

        assertEquals(items.toSet(), selection.ids)
    }

    @Test
    fun `retain elimine les elements disparus apres un rafraichissement`() {
        val selection = Selection()
        selection.selectAll(items)
        selection.retain(setOf("a", "b"))

        assertEquals(setOf("a", "b"), selection.ids)
        assertFalse(selection.contains("c"))
    }

    @Test
    fun `retain oublie l'ancre disparue et le maj-clic suivant reste sain`() {
        val selection = Selection()
        selection.click("e", items, ctrl = false, shift = false)
        selection.retain(setOf("a", "b", "c"))
        selection.click("b", items, ctrl = false, shift = true)

        // L'ancre ayant disparu, on retombe sur une sélection simple plutôt
        // que sur une plage calculée depuis un élément qui n'existe plus.
        assertEquals(setOf("b"), selection.ids)
    }

    @Test
    fun `clear vide la selection`() {
        val selection = Selection()
        selection.selectAll(items)
        selection.clear()

        assertTrue(selection.isEmpty)
        assertEquals(0, selection.size)
    }
}
