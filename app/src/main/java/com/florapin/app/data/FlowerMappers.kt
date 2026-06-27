package com.florapin.app.data

import com.florapin.app.network.dto.CreateFlowerRequest
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.dto.PushItem
import java.time.Instant

/** Conversions entre l'entité Room locale et les DTOs de l'API. */

private fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

private fun String.isoToEpochMillis(): Long = Instant.parse(this).toEpochMilli()

/** Métadonnées de capture locale → requête de création serveur. */
fun FlowerEntity.toCreateRequest(): CreateFlowerRequest = CreateFlowerRequest(
    takenAt = createdAt.toIso(),
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyMeters?.toDouble(),
    notes = notes,
    visibility = visibility,
    feedIncludeGps = feedIncludeGps,
    species = species,
    tags = tags.ifEmpty { null },
)

/** Élément de push (sync par lot), identifié par l'id local. */
fun FlowerEntity.toPushItem(): PushItem = PushItem(
    localId = id.toString(),
    takenAt = createdAt.toIso(),
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyMeters?.toDouble(),
    notes = notes,
    visibility = visibility,
    feedIncludeGps = feedIncludeGps,
    species = species,
    tags = tags.ifEmpty { null },
)

/**
 * Applique l'état serveur à une ligne locale existante (réconciliation) :
 * conserve l'id local et le chemin image, marque comme synchronisée.
 */
fun FlowerDto.applyTo(local: FlowerEntity): FlowerEntity = local.copy(
    serverId = id,
    notes = notes,
    latitude = latitude ?: local.latitude,
    longitude = longitude ?: local.longitude,
    accuracyMeters = accuracyM?.toFloat() ?: local.accuracyMeters,
    updatedAt = updatedAt.isoToEpochMillis(),
    syncState = SyncState.SYNCED.name,
    remoteImageUrl = imageUrl,
    remoteThumbnailUrl = thumbnailUrl,
    species = species ?: local.species,
    speciesId = speciesId ?: local.speciesId,
    speciesScientificName = speciesRef?.scientificName ?: local.speciesScientificName,
    speciesCommonName = speciesRef?.commonName ?: local.speciesCommonName,
    tags = tags.ifEmpty { local.tags },
    visibility = visibility,
    feedIncludeGps = feedIncludeGps,
    ownerId = ownerId,
)

/**
 * Crée une entité locale à partir d'une fleur serveur (ex. fleur d'un autre
 * appareil reçue par sync). `imagePath` est vide tant que l'image n'est pas
 * téléchargée ; l'`imageUrl` présignée sert à l'affichage distant.
 */
fun FlowerDto.toEntity(imagePath: String = ""): FlowerEntity = FlowerEntity(
    imagePath = imagePath,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracyM?.toFloat(),
    createdAt = takenAt.isoToEpochMillis(),
    notes = notes,
    serverId = id,
    syncState = SyncState.SYNCED.name,
    updatedAt = updatedAt.isoToEpochMillis(),
    remoteImageUrl = imageUrl,
    remoteThumbnailUrl = thumbnailUrl,
    species = species,
    speciesId = speciesId,
    speciesScientificName = speciesRef?.scientificName,
    speciesCommonName = speciesRef?.commonName,
    tags = tags,
    visibility = visibility,
    feedIncludeGps = feedIncludeGps,
    ownerId = ownerId,
)
