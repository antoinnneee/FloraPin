package com.florapin.app.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.PhotoRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Variantes finales envoyées au serveur. */
data class CompressedImageVariants(val full: File, val thumbnail: File)

/**
 * Compression locale des captures, hors du thread UI.
 *
 * Le profil standard produit un WebP 3 200 px/Q70 et le premium 4 000 px/Q90.
 * Lanczos3 et l'encodage restent hors du thread UI. Pendant la bêta, la politique
 * de qualité accorde le profil premium à tous les comptes.
 */
class ImageCompressionEngine(
    private val flowers: FlowerRepository,
    private val photos: PhotoRepository,
    private val profileProvider: () -> ImageEncodingProfile = {
        ImageQualityPolicy.currentProfile()
    },
) {
    suspend fun compressPending(): Boolean = compressionMutex.withLock {
        withContext(Dispatchers.IO) {
            var allSucceeded = true
            flowers.imagesForCompression().forEach { flower ->
                val source = File(flower.imagePath)
                try {
                    val variants = compress(source)
                    flowers.replaceImagePath(flower.id, flower.imagePath, variants.full.absolutePath)
                    source.delete()
                } catch (_: Exception) {
                    allSucceeded = false
                }
            }
            photos.imagesForCompression().forEach { photo ->
                val source = File(photo.imagePath)
                try {
                    val variants = compress(source)
                    photos.replaceImagePath(photo.id, photo.imagePath, variants.full.absolutePath)
                    source.delete()
                } catch (_: Exception) {
                    allSucceeded = false
                }
            }
            allSucceeded
        }
    }

    private fun compress(source: File): CompressedImageVariants {
        require(source.isFile && source.length() > 0L) { "Capture locale absente" }
        val full = PhotoStorage.webpFileFor(source)
        val thumbnail = PhotoStorage.thumbnailFileFor(full)
        val fullTemp = File(full.parentFile, "${full.name}.tmp")
        val thumbnailTemp = File(thumbnail.parentFile, "${thumbnail.name}.tmp")

        val decoded = requireNotNull(BitmapFactory.decodeFile(source.absolutePath)) {
            "Décodage impossible"
        }
        val oriented = orient(decoded, source)
        if (oriented !== decoded) decoded.recycle()
        val profile = profileProvider()
        val fullBitmap = LanczosBitmapScaler.scaleInside(oriented, profile.maxEdge)
        if (fullBitmap !== oriented) oriented.recycle()
        val thumbBitmap = LanczosBitmapScaler.scaleInside(fullBitmap, THUMBNAIL_MAX_EDGE)
        try {
            encodeWebp(fullBitmap, fullTemp, profile.webpQuality)
            encodeWebp(thumbBitmap, thumbnailTemp, THUMBNAIL_QUALITY)
            checkDecodable(fullTemp)
            checkDecodable(thumbnailTemp)
            replaceAtomically(fullTemp, full)
            replaceAtomically(thumbnailTemp, thumbnail)
            return CompressedImageVariants(full, thumbnail)
        } finally {
            fullTemp.delete()
            thumbnailTemp.delete()
            if (thumbBitmap !== fullBitmap) thumbBitmap.recycle()
            fullBitmap.recycle()
        }
    }

    private fun orient(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(270f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun encodeWebp(bitmap: Bitmap, target: File, quality: Int) {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        target.outputStream().buffered().use { output ->
            check(bitmap.compress(format, quality, output)) { "Encodage WebP impossible" }
        }
        check(target.length() > 0L) { "WebP vide" }
    }

    private fun checkDecodable(file: File) {
        val decoded = BitmapFactory.decodeFile(file.absolutePath)
        check(decoded != null) { "WebP illisible" }
        decoded.recycle()
    }

    private fun replaceAtomically(temp: File, target: File) {
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Impossible de finaliser ${target.name}" }
    }

    companion object {
        const val THUMBNAIL_MAX_EDGE = 400
        const val THUMBNAIL_QUALITY = 70
        private val compressionMutex = Mutex()
    }
}
