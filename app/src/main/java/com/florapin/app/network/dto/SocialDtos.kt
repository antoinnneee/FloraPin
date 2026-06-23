package com.florapin.app.network.dto

import com.squareup.moshi.JsonClass

/** DTOs amis & partages. */
@JsonClass(generateAdapter = true)
data class CreateFriendshipRequest(
    val email: String,
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

@JsonClass(generateAdapter = true)
data class CreateShareRequest(
    val friendId: String,
    val scope: String,
    val flowerId: String? = null,
    val albumId: String? = null,
    val includeGps: Boolean? = null,
)

@JsonClass(generateAdapter = true)
data class ShareDto(
    val id: String,
    val ownerId: String,
    val sharedWith: String,
    val scope: String,
    val flowerId: String? = null,
    val albumId: String? = null,
    val includeGps: Boolean,
    val createdAt: String,
)
