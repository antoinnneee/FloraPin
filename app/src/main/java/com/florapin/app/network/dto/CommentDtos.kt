package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs du fil de discussion sur une fleur. */

/** Corps de POST flowers/{id}/comments : un commentaire libre. */
@JsonClass(generateAdapter = true)
data class CreateCommentRequest(
    val body: String,
)

/** Commentaire renvoyé par l'API, enrichi du nom de l'auteur. */
@JsonClass(generateAdapter = true)
data class FlowerCommentDto(
    val id: String,
    val flowerId: String,
    val authoredBy: String,
    /** Nom d'affichage de l'auteur (peut être vide). */
    val authorName: String = "",
    val body: String,
    /** Le lecteur courant peut-il supprimer ce commentaire ? */
    val canDelete: Boolean = false,
    val createdAt: String,
)
