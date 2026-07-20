package com.florapin.app.badges

/**
 * Source d'un badge affiché dans la grille (TÂCHE 5.5).
 *
 * - [COLLECTION] : calcul 100 % local ([BadgeCalculator]) — disponible hors-ligne.
 * - [ENTRAIDE] : compteurs collaboratifs calculés côté serveur (TÂCHE 5.4) —
 *   nécessitent le réseau ; grisés hors-ligne (device-first).
 */
enum class BadgeSource { COLLECTION, ENTRAIDE }

/**
 * Métadonnées d'affichage d'une **famille** de badge (TÂCHE 5.5) : indépendantes
 * de la couche UI (pas de dépendance Compose) et de la valeur courante. La grille
 * combine ces définitions avec la progression (locale) ou les compteurs (serveur)
 * pour produire l'état visuel des cartes.
 *
 * Choix de regroupement : les saisons (4 badges à palier unique + « Quatre
 * saisons ») et l'outre-mer (un badge par région visitée) sont présentés comme
 * **une seule famille à paliers** (« 3 / 4 », « 2 / 5 ») plutôt qu'en cartes
 * séparées, conformément à la DA « familles ».
 *
 * @param id identifiant de la famille. Pour l'entraide, il correspond au champ du
 *   DTO serveur ([com.florapin.app.network.dto.EntraideBadgeCountsDto]) ; pour la
 *   collection, à une clé de progression ([BadgeCalculator.Progress]).
 * @param tiers seuils cumulatifs (une étoile par seuil). Une famille à palier
 *   unique (première fleur) utilise `[1]`.
 */
data class BadgeDef(
    val id: String,
    val emoji: String,
    val label: String,
    val tiers: List<Int>,
    val source: BadgeSource,
    val description: String = label,
)

/**
 * Catalogue ordonné des familles de badges affichées dans l'onglet Badges du
 * Profil (TÂCHE 5.5). Les seuils « entraide » qui n'étaient pas figés par le plan
 * (propositions, commentaires, réactions) sont arrêtés ici — point unique à
 * ajuster si la courbe de progression doit évoluer.
 */
object BadgeCatalog {

    /** Familles « collection » (calcul local, disponibles hors-ligne). */
    val COLLECTION: List<BadgeDef> = listOf(
        BadgeDef(BadgeCalculator.PREMIERE_FLEUR, "🌸", "Première fleur", listOf(1), BadgeSource.COLLECTION,
            "Enregistrez votre première fleur dans FloraPin."),
        BadgeDef(BadgeCalculator.HERBIER, "📚", "Herbier", listOf(10, 50, 100, 250), BadgeSource.COLLECTION,
            "Progressez en enregistrant de nouvelles fleurs dans votre herbier."),
        BadgeDef(BadgeCalculator.DIVERSITE, "🌿", "Diversité", listOf(10, 25, 50), BadgeSource.COLLECTION,
            "Identifiez des espèces différentes dans votre collection."),
        BadgeDef(SAISONS, "🍂", "Saisons", listOf(1, 2, 3, 4), BadgeSource.COLLECTION,
            "Photographiez au moins une fleur pendant chaque saison."),
        BadgeDef(BadgeCalculator.EXPLORATEUR, "🧭", "Explorateur", listOf(2, 5, 10, 15, 18), BadgeSource.COLLECTION,
            "Photographiez des fleurs dans différentes régions françaises."),
        BadgeDef(OUTRE_MER, "🏝️", "Outre-mer", listOf(1, 2, 3, 4, 5), BadgeSource.COLLECTION,
            "Photographiez des fleurs dans différentes régions d'outre-mer."),
        BadgeDef(BadgeCalculator.LIEUX_DISTINCTS, "📍", "Lieux distincts", listOf(5, 15, 30, 50, 100), BadgeSource.COLLECTION,
            "Explorez de nouveaux lieux géographiques avec vos fleurs."),
    )

    /** Familles « entraide » (compteurs serveur, grisées hors-ligne). */
    val ENTRAIDE: List<BadgeDef> = listOf(
        BadgeDef(FRIENDS, "🤝", "Amis", listOf(1, 3, 5, 10), BadgeSource.ENTRAIDE,
            "Ajoutez des amis à votre réseau FloraPin."),
        BadgeDef(PROPOSALS_ACCEPTED, "🎓", "Identifications acceptées", listOf(1, 5, 10, 25, 50), BadgeSource.ENTRAIDE,
            "Aidez vos amis avec des identifications qu'ils acceptent."),
        BadgeDef(PROPOSALS_MADE, "🔍", "Propositions faites", listOf(1, 10, 25), BadgeSource.ENTRAIDE,
            "Proposez une espèce pour les fleurs de vos amis."),
        BadgeDef(PROPOSALS_ACCEPTED_AS_OWNER, "✅", "Propositions validées", listOf(1, 10, 25), BadgeSource.ENTRAIDE,
            "Validez les propositions reçues sur vos propres fleurs."),
        BadgeDef(COMMENTS, "💬", "Commentaires", listOf(1, 10, 50), BadgeSource.ENTRAIDE,
            "Participez aux discussions en commentant des fleurs."),
        BadgeDef(REACTIONS_GIVEN, "👍", "Réactions données", listOf(1, 25, 100), BadgeSource.ENTRAIDE,
            "Réagissez aux fleurs partagées par vos amis."),
        BadgeDef(REACTIONS_RECEIVED, "❤️", "Réactions reçues", listOf(1, 25, 100), BadgeSource.ENTRAIDE,
            "Recevez des réactions sur les fleurs que vous partagez."),
    )

    /** Toutes les familles, dans l'ordre d'affichage (collection puis entraide). */
    val ALL: List<BadgeDef> = COLLECTION + ENTRAIDE

    // --- Familles « collection » agrégées (ids synthétiques, pas ceux du calcul) ---
    /** 🍂 Saisons (agrège les 4 saisons + « Quatre saisons »). */
    const val SAISONS = "saisons"

    /** 🏝️ Outre-mer (agrège les badges `outremer:CODE` par région visitée). */
    const val OUTRE_MER = "outremer"

    // --- Familles « entraide » (ids alignés sur EntraideBadgeCountsDto) ---
    const val FRIENDS = "friends"
    const val PROPOSALS_MADE = "proposalsMade"
    const val PROPOSALS_ACCEPTED = "proposalsAccepted"
    const val PROPOSALS_ACCEPTED_AS_OWNER = "proposalsAcceptedAsOwner"
    const val COMMENTS = "comments"
    const val REACTIONS_GIVEN = "reactionsGiven"
    const val REACTIONS_RECEIVED = "reactionsReceived"
    // NB : `identificationRequests` (demandes ouvertes) est volontairement absent :
    // ce compteur **transitoire** (il redescend quand une fleur est identifiée) ne
    // convient pas à un badge cumulatif.
}
