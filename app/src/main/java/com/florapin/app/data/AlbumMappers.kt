package com.florapin.app.data

import com.florapin.app.network.dto.AlbumDto
import java.time.Instant

/** Conversions entre l'entité album locale et le DTO serveur (NODE-102). */

private fun String.isoToEpochMillis(): Long = Instant.parse(this).toEpochMilli()

/** Crée une entité locale synchronisée à partir d'un album serveur. */
fun AlbumDto.toEntity(): AlbumEntity {
    val created = createdAt.isoToEpochMillis()
    return AlbumEntity(
        serverId = id,
        name = name,
        ownerId = ownerId,
        createdAt = created,
        updatedAt = created,
        syncState = SyncState.SYNCED.name,
    )
}

/** Applique l'état serveur à un album local existant (réconciliation). */
fun AlbumDto.applyTo(local: AlbumEntity): AlbumEntity = local.copy(
    serverId = id,
    name = name,
    ownerId = ownerId,
    syncState = SyncState.SYNCED.name,
)
