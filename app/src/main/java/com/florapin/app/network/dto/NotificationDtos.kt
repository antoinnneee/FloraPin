package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs du centre de notifications in-app (TÂCHE 2.7). */

/**
 * Notification in-app persistée côté serveur. [data] porte les identifiants
 * BRUTS du contexte (jamais de nom d'affichage figé, cf. 2.1) : selon le type,
 * `flowerId`, `byUserId`/`fromUserId`, `species`, `albumId`… Toutes les valeurs
 * actuelles sont des chaînes (ou `null` pour un partage sans fleur ciblée).
 * [readAt] est `null` tant que la notification n'a pas été lue.
 */
@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: String,
    val type: String,
    val data: Map<String, String?> = emptyMap(),
    val readAt: String? = null,
    val createdAt: String,
) {
    /** L'identifiant SERVEUR de la fleur concernée, s'il y en a une. */
    val flowerServerId: String?
        get() = data["flowerId"]?.takeIf { it.isNotBlank() }

    /** Espèce transmise (proposition/confirmation d'espèce), si présente. */
    val species: String?
        get() = data["species"]?.takeIf { it.isNotBlank() }
}

/** Réponse de `GET notifications/unread-count`. */
@JsonClass(generateAdapter = true)
data class UnreadCountDto(
    val count: Int = 0,
)

/** Nombre de notifications effectivement passées à l'état lu. */
@JsonClass(generateAdapter = true)
data class MarkAllReadDto(
    val updated: Int = 0,
)
