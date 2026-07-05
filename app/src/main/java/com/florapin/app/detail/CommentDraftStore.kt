package com.florapin.app.detail

import android.content.Context

/**
 * Persistance locale des brouillons de commentaires, indexés par fleur
 * ([flowerServerId]). Le texte saisi dans le fil survit ainsi à la fermeture de
 * la bottom sheet (et à un redémarrage de l'appli / mort du process).
 *
 * Interface découplée du stockage pour rester testable ; l'implémentation par
 * défaut s'appuie sur un fichier de prefs DÉDIÉ (`florapin_comment_drafts`) — on
 * ne touche jamais au fichier partagé `florapin_sync`.
 */
interface CommentDraftStore {
    /** Brouillon persisté pour [flowerServerId], ou "" s'il n'y en a pas. */
    fun load(flowerServerId: String): String

    /** Persiste le brouillon (ou l'efface s'il est vide) pour [flowerServerId]. */
    fun save(flowerServerId: String, draft: String)

    /** Efface le brouillon de [flowerServerId] (après envoi réussi). */
    fun clear(flowerServerId: String) = save(flowerServerId, "")
}

/** Implémentation SharedPreferences (fichier dédié, jamais `.clear()`). */
class PrefsCommentDraftStore(context: Context) : CommentDraftStore {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(flowerServerId: String): String =
        prefs.getString(key(flowerServerId), "") ?: ""

    override fun save(flowerServerId: String, draft: String) {
        prefs.edit().apply {
            // Un brouillon vide ne mérite pas d'entrée : on retire la clé plutôt
            // que de laisser une chaîne vide traîner (borne la taille du fichier).
            if (draft.isEmpty()) remove(key(flowerServerId))
            else putString(key(flowerServerId), draft)
        }.apply()
    }

    private fun key(flowerServerId: String) = "draft_$flowerServerId"

    private companion object {
        const val PREFS = "florapin_comment_drafts"
    }
}
