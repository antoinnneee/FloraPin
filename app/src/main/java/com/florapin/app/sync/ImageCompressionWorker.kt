package com.florapin.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.florapin.app.capture.ImageCompressionEngine
import com.florapin.app.data.FlowerRepository
import com.florapin.app.data.PhotoRepository

/** Convertit les captures en WebP en arrière-plan, même en mode 100 % local. */
class ImageCompressionWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ok = ImageCompressionEngine(
            FlowerRepository.from(applicationContext),
            PhotoRepository.from(applicationContext),
        ).compressPending()
        return if (ok) Result.success() else Result.retry()
    }
}
