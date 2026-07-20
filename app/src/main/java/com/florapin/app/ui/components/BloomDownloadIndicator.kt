package com.florapin.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.florapin.app.R
import kotlin.math.roundToInt

private val bloomFrames = intArrayOf(
    R.drawable.bloom_download_01,
    R.drawable.bloom_download_02,
    R.drawable.bloom_download_03,
    R.drawable.bloom_download_04,
    R.drawable.bloom_download_05,
    R.drawable.bloom_download_06,
    R.drawable.bloom_download_07,
    R.drawable.bloom_download_08,
)

/**
 * Fleur qui s'ouvre à mesure qu'un téléchargement progresse.
 *
 * Quand [progress] est nul, les huit images sont jouées en boucle pour les
 * opérations dont l'avancement n'est pas encore connu.
 */
@Composable
fun BloomDownloadIndicator(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    contentDescription: String? = null,
) {
    val frameIndex = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "download-bloom")
        val animatedFrame by transition.animateFloat(
            initialValue = 0f,
            targetValue = bloomFrames.size - 0.001f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "download-bloom-frame",
        )
        animatedFrame.toInt().coerceIn(bloomFrames.indices)
    } else {
        bloomFrameIndex(progress)
    }

    Image(
        painter = painterResource(bloomFrames[frameIndex]),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}

internal fun bloomFrameIndex(progress: Float): Int =
    (progress.coerceIn(0f, 1f) * (bloomFrames.size - 1))
        .roundToInt()
        .coerceIn(bloomFrames.indices)

