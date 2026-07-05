package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs amis & partages. */
@JsonClass(generateAdapter = true)
data class CreateFriendshipRequest(
    val email: String,
)

/** Ajout d'ami par QR code (TÂCHE 4.5) : on transmet l'id (UUID), pas l'email. */
@JsonClass(generateAdapter = true)
data class AddFriendByIdRequest(
    val userId: String,
)

@JsonClass(generateAdapter = true)
data class FriendUserDto(
    val id: String,
    val displayName: String,
    val email: String,
)

@JsonClass(generateAdapter = true)
data class FriendshipDto(
    val id: String,
    val status: String,
    val direction: String,
    val user: FriendUserDto,
    val createdAt: String,
)

/**
 * Profil public limité d'un ami (TÂCHE 5.7) : identité, ancienneté (compte +
 * amitié), amis en commun, espèces communes et fleurs de l'ami visibles par moi
 * (partagées ou diffusées au réseau). Ne contient jamais ses stats privées.
 */
@JsonClass(generateAdapter = true)
data class FriendProfileDto(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
    /** Inscription de l'ami (ISO8601). */
    val memberSince: String,
    /** Amitié acceptée depuis (ISO8601) — « Amis depuis mai 2026 ». */
    val friendsSince: String,
    val mutualFriendsCount: Int = 0,
    val visibleFlowerCount: Int = 0,
    val commonSpecies: List<String> = emptyList(),
    val sharedFlowers: List<FlowerDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class CreateShareRequest(
    val friendId: String,
    val scope: String,
    val flowerId: String? = null,
    val albumId: String? = null,
    val includeGps: Boolean? = null,
)

/** Partage d'un périmètre avec tous les amis acceptés (sans destinataire). */
@JsonClass(generateAdapter = true)
data class ShareToAllFriendsRequest(
    val scope: String,
    val flowerId: String? = null,
    val albumId: String? = null,
    val includeGps: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class ShareDto(
    val id: String,
    val ownerId: String,
    /** null quand audience = "all_friends" (partage à tout le réseau). */
    val sharedWith: String? = null,
    /** "friend" (ami précis) ou "all_friends" (réseau, amis présents et futurs). */
    val audience: String = "friend",
    val scope: String,
    val flowerId: String? = null,
    val albumId: String? = null,
    val includeGps: Boolean,
    val createdAt: String,
)
