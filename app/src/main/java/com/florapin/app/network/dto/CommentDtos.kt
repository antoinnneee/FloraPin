package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs du fil de discussion sur une fleur. */

/** Corps de POST flowers/{id}/comments : un commentaire libre. */
@JsonClass(generateAdapter = true)
data class CreateCommentRequest(
    val body: String,
    /**
     * Réponse citée : id du commentaire auquel on répond, `null` au premier
     * niveau. Le serveur aplatit vers la racine (fil à un seul niveau).
     */
    val replyToId: String? = null,
)

/** Corps de PATCH flowers/{id}/comments/{commentId} : le nouveau texte. */
@JsonClass(generateAdapter = true)
data class UpdateCommentRequest(
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
    /** Le lecteur courant peut-il éditer ce commentaire ? (auteur uniquement). */
    val canEdit: Boolean = false,
    val createdAt: String,
    /** Dernière édition par l'auteur, `null` si jamais modifié. */
    val editedAt: String? = null,
    /** Réponse citée : id du commentaire racine visé, `null` au premier niveau. */
    val replyToId: String? = null,
    /** Nom d'affichage de l'auteur du commentaire cité (`null` si sans réponse). */
    val replyToAuthorName: String? = null,
    /** Texte du commentaire cité (`null` si sans réponse). */
    val replyToBody: String? = null,
    /**
     * Amis mentionnés (`@[userId]`) dans [body], avec leur nom d'affichage
     * COURANT. Le corps encode l'identifiant (pas le nom) : un renommage ne casse
     * pas la mention, le nom est simplement re-résolu à chaque lecture (TÂCHE 1.7).
     */
    val mentions: List<CommentMentionDto> = emptyList(),
)

/** Une mention résolue : identifiant encodé dans le corps + nom d'affichage. */
@JsonClass(generateAdapter = true)
data class CommentMentionDto(
    val userId: String,
    val displayName: String,
)
