package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** Groupe collaboratif tel que renvoyé par le backend (TÂCHE 7.1). */
@JsonClass(generateAdapter = true)
data class GroupDto(
    val id: String,
    val ownerId: String,
    val name: String,
    val clientId: String? = null,
    /** Rôle du compte courant dans ce groupe : 'owner' | 'member'. */
    val role: String,
    /** Statut d'appartenance du compte courant : 'pending' | 'accepted'. */
    val status: String,
    val members: List<GroupMemberDto> = emptyList(),
    val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class GroupMemberDto(
    val userId: String,
    val displayName: String,
    val role: String,
    val status: String,
)

@JsonClass(generateAdapter = true)
data class CreateGroupRequest(
    val name: String,
    val clientId: String? = null,
)

@JsonClass(generateAdapter = true)
data class UpdateGroupRequest(
    val name: String,
)

/** Invitation d'un ami au groupe (par UUID utilisateur). */
@JsonClass(generateAdapter = true)
data class InviteMemberRequest(
    val userId: String,
)
