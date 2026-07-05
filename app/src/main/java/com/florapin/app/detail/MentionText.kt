package com.florapin.app.detail

/**
 * Encodage et rendu des mentions `@ami` dans le corps d'un commentaire.
 *
 * Une mention est stockée dans le texte sous la forme `@[userId]` — on encode
 * l'IDENTIFIANT, jamais le nom d'affichage : ainsi un renommage (TÂCHE 1.7) ne
 * casse pas la mention, le nom est simplement re-résolu à l'affichage à partir de
 * la table `id → nom` fournie (amis chargés, ou `mentions` renvoyées par l'API).
 *
 * Logique PURE (sans dépendance Android/Compose) pour rester testable en unit
 * test ; l'UI (autocomplete, coloration, transformation visuelle) s'appuie dessus.
 */
object MentionText {

    /** Motif d'une mention encodée `@[userId]` (UUID). Groupe 1 = l'identifiant. */
    val MENTION_REGEX =
        Regex("@\\[([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})]")

    /** Encode une mention vers sa forme stockée `@[userId]`. */
    fun encode(userId: String): String = "@[$userId]"

    /**
     * Requête d'autocomplete « en cours de frappe » : le fragment `@xxx` situé en
     * fin de texte (le plus courant), s'il n'est pas déjà une mention encodée.
     * Retourne le texte tapé après le `@` (éventuellement vide juste après `@`),
     * ou `null` s'il n'y a pas de mention en cours (espace, ponctuation, ou token
     * déjà fermé). On se limite volontairement à la fin du texte : suffisant pour
     * une saisie linéaire, sans dépendre de la position du curseur.
     */
    fun activeQuery(text: String): String? {
        val at = text.lastIndexOf('@')
        if (at < 0) return null
        val after = text.substring(at + 1)
        // `@[` = début d'un token encodé (ou déjà fermé) : pas une saisie libre.
        if (after.startsWith("[")) return null
        // La requête ne court que jusqu'à un séparateur : lettres/chiffres/_ only.
        if (after.any { !it.isLetterOrDigit() && it != '_' }) return null
        return after
    }

    /**
     * Remplace la requête d'autocomplete en cours (`@xxx` final) par la mention
     * encodée de [userId], suivie d'une espace pour poursuivre la saisie. Si aucune
     * requête n'est active, ajoute simplement la mention en fin de texte.
     */
    fun insertMention(text: String, userId: String): String {
        val at = text.lastIndexOf('@')
        val prefix = if (at >= 0 && activeQuery(text) != null) text.substring(0, at) else text
        return prefix + encode(userId) + " "
    }

    /**
     * Rend le corps en clair pour l'affichage : chaque `@[userId]` devient
     * `@Nom` d'après [nameById] (repli « @quelqu'un » si l'id est inconnu, ex.
     * ancien ami retiré). Utilisé pour l'accessibilité / repli sans style.
     */
    fun render(text: String, nameById: Map<String, String>): String =
        MENTION_REGEX.replace(text) { match ->
            val name = nameById[match.groupValues[1]].orEmpty().ifBlank { "quelqu'un" }
            "@$name"
        }

    /** Un segment du corps : soit du texte brut, soit une mention (id + nom rendu). */
    sealed interface Segment {
        data class Literal(val text: String) : Segment
        data class Mention(val userId: String, val display: String) : Segment
    }

    /**
     * Découpe [text] en segments littéraux et mentions, en résolvant le nom via
     * [nameById]. Permet à l'UI de construire un `AnnotatedString` (mentions
     * colorées) sans réimplémenter le parsing.
     */
    fun segments(text: String, nameById: Map<String, String>): List<Segment> {
        val out = mutableListOf<Segment>()
        var index = 0
        for (match in MENTION_REGEX.findAll(text)) {
            if (match.range.first > index) {
                out.add(Segment.Literal(text.substring(index, match.range.first)))
            }
            val userId = match.groupValues[1]
            val name = nameById[userId].orEmpty().ifBlank { "quelqu'un" }
            out.add(Segment.Mention(userId, "@$name"))
            index = match.range.last + 1
        }
        if (index < text.length) {
            out.add(Segment.Literal(text.substring(index)))
        }
        return out
    }
}
