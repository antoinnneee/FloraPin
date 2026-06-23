package com.florapin.app.data

import com.florapin.app.network.dto.PhotoDto

/** Conversions entre l'entité photo locale et le DTO serveur (NODE-107). */

/** Crée une entité locale synchronisée à partir d'une photo serveur distante. */
fun PhotoDto.toEntity(flowerLocalId: Long): PhotoEntity = PhotoEntity(
    flowerLocalId = flowerLocalId,
    serverId = id,
    imagePath = "",
    remoteUrl = url,
    position = position,
    isCover = isCover,
    syncState = SyncState.SYNCED.name,
)

/** Applique l'état serveur à une photo locale existante. */
fun PhotoDto.applyTo(local: PhotoEntity): PhotoEntity = local.copy(
    serverId = id,
    remoteUrl = url,
    position = position,
    isCover = isCover,
    syncState = SyncState.SYNCED.name,
)
