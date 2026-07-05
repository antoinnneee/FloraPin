package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/**
 * Compteurs d'entraide calculés côté serveur (GET /me/badges, TÂCHE 5.4).
 *
 * Le serveur ne renvoie que des **compteurs bruts** : le mapping vers les paliers
 * (étoiles, progression « 34 / 50 ») et la fusion avec les badges « collection »
 * locaux se font côté app (TÂCHE 5.5). Ces données collaboratives vivent côté
 * serveur : indisponibles hors-ligne, l'app retombe alors sur une dernière valeur
 * mise en cache ou grise les badges concernés (dégradation device-first).
 *
 * Tous les champs ont une valeur par défaut : un serveur plus ancien (ou une
 * réponse partielle) ne fait pas planter le parsing.
 */
@JsonClass(generateAdapter = true)
data class EntraideBadgeCountsDto(
    /** 🤝 Amis acceptés (paliers 1/3/5/10). */
    val friends: Int = 0,
    /** 🔍 Propositions d'espèce que j'ai faites (toutes). */
    val proposalsMade: Int = 0,
    /** 🎓 Mes propositions acceptées (paliers 1/5/10/25/50). */
    val proposalsAccepted: Int = 0,
    /** ❓ Demandes d'identification ouvertes sur mes fleurs. */
    val identificationRequests: Int = 0,
    /** ✅ Propositions que j'ai acceptées en tant que propriétaire. */
    val proposalsAcceptedAsOwner: Int = 0,
    /** 💬 Commentaires que j'ai postés. */
    val comments: Int = 0,
    /** 👍 Réactions données (paliers). */
    val reactionsGiven: Int = 0,
    /** ❤️ Réactions reçues des autres sur mes fleurs (paliers). */
    val reactionsReceived: Int = 0,
)
