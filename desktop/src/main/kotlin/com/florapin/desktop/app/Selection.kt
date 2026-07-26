package com.florapin.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Sélection multiple aux conventions de l'Explorateur Windows, que tout
 * utilisateur de PC applique sans y penser :
 *
 * - clic simple : ne garde que l'élément cliqué ;
 * - Ctrl+clic : ajoute ou retire, sans toucher au reste ;
 * - Maj+clic : étend depuis le dernier point d'ancrage jusqu'à l'élément visé.
 *
 * L'ancre est le dernier élément cliqué sans Maj — c'est bien ce comportement,
 * et non « le dernier élément sélectionné », qui permet d'élargir puis de
 * rétrécir une plage par Maj+clics successifs.
 */
class Selection {

    var ids by mutableStateOf<Set<String>>(emptySet())
        private set

    private var anchor: String? = null

    val size: Int get() = ids.size
    val isEmpty: Boolean get() = ids.isEmpty()

    fun contains(id: String): Boolean = id in ids

    /** Point d'entrée unique d'un clic sur un élément de la liste [ordered]. */
    fun click(id: String, ordered: List<String>, ctrl: Boolean, shift: Boolean) {
        when {
            shift && anchor != null -> selectRange(anchor!!, id, ordered, additive = ctrl)
            ctrl -> {
                ids = if (id in ids) ids - id else ids + id
                anchor = id
            }
            else -> {
                ids = setOf(id)
                anchor = id
            }
        }
    }

    fun selectAll(ordered: List<String>) {
        ids = ordered.toSet()
        anchor = ordered.lastOrNull()
    }

    fun clear() {
        ids = emptySet()
        anchor = null
    }

    /** Sélectionne un seul élément (clic droit hors sélection, navigation clavier). */
    fun selectOnly(id: String) {
        ids = setOf(id)
        anchor = id
    }

    /**
     * Retire les identifiants disparus après un rafraîchissement : sans cela,
     * une photo supprimée resterait comptée dans « n sélectionnées » et les
     * actions groupées porteraient sur des identifiants morts.
     */
    fun retain(existing: Set<String>) {
        if (ids.any { it !in existing }) ids = ids.filterTo(mutableSetOf()) { it in existing }
        if (anchor !in existing) anchor = null
    }

    private fun selectRange(from: String, to: String, ordered: List<String>, additive: Boolean) {
        val start = ordered.indexOf(from)
        val end = ordered.indexOf(to)
        if (start < 0 || end < 0) {
            selectOnly(to)
            return
        }
        val range = ordered.subList(minOf(start, end), maxOf(start, end) + 1).toSet()
        // L'ancre reste volontairement inchangée : un second Maj+clic doit
        // repartir du même point de départ.
        ids = if (additive) ids + range else range
    }
}
