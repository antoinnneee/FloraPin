package com.florapin.app.network

import com.florapin.app.network.api.FlowersApi
import com.florapin.app.network.dto.CreateFlowerRequest
import com.florapin.app.network.dto.FlowerDto
import com.florapin.app.network.upload.ImageUploader
import java.io.File

/**
 * Accès distant aux fleurs : encapsule l'upload présigné en 2 temps.
 *
 * 1. `POST /flowers` (métadonnées) → fleur serveur + URL présignée PUT.
 * 2. PUT du JPEG directement sur le stockage objet via [ImageUploader].
 *
 * La fleur serveur renvoyée porte l'`id` (UUID) à réconcilier en base locale et
 * l'`imageUrl` présignée (GET) consommable par Coil. L'URL de lecture peut être
 * régénérée via [refreshImageUrl].
 */
class RemoteFlowerSource(
    private val flowersApi: FlowersApi,
    private val imageUploader: ImageUploader,
) {
    /** Crée la fleur côté serveur puis téléverse son image. Renvoie la fleur. */
    suspend fun uploadFlower(
        request: CreateFlowerRequest,
        imageFile: File,
    ): FlowerDto {
        val created = flowersApi.create(request)
        imageUploader.upload(created.upload.url, imageFile)
        return created.flower
    }

    /** (Re)génère l'URL présignée de lecture de l'image. */
    suspend fun refreshImageUrl(flowerId: String): String =
        flowersApi.imageUrl(flowerId).imageUrl
}
