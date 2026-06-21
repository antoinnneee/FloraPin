package com.florapin.app.network.upload

import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Téléverse un fichier image directement vers une URL présignée (PUT), sans
 * passer par l'API — les octets ne transitent jamais par le backend.
 */
class ImageUploader(private val client: OkHttpClient) {

    suspend fun upload(
        uploadUrl: String,
        file: File,
        contentType: String = "image/jpeg",
    ) = withContext(Dispatchers.IO) {
        val body = file.asRequestBody(contentType.toMediaType())
        val request = Request.Builder().url(uploadUrl).put(body).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Échec de l'upload image (HTTP ${response.code})")
            }
        }
    }
}
