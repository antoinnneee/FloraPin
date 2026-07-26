package com.florapin.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.florapin.desktop.core.ImageStore

/** États successifs du chargement d'une image distante. */
private sealed interface PhotoState {
    data object Loading : PhotoState
    data class Ready(val bitmap: ImageBitmap) : PhotoState
    data object Failed : PhotoState
}

@Composable
private fun rememberPhoto(url: String?): State<PhotoState> =
    produceState<PhotoState>(PhotoState.Loading, url) {
        if (url.isNullOrBlank()) {
            value = PhotoState.Failed
            return@produceState
        }
        // Une image déjà décodée s'affiche sans passer par l'état de
        // chargement : sans cela, faire défiler la grille ferait clignoter en
        // gris des vignettes pourtant présentes en mémoire.
        ImageStore.peek(url)?.let {
            value = PhotoState.Ready(it)
            return@produceState
        }
        value = PhotoState.Loading
        val bitmap = ImageStore.load(url)
        value = if (bitmap != null) PhotoState.Ready(bitmap) else PhotoState.Failed
    }

/**
 * Image distante avec cache, fondu à l'arrivée et état d'échec explicite.
 * Le fondu évite l'à-coup visuel d'une grille qui se remplit par blocs.
 */
@Composable
fun AsyncPhoto(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = null,
) {
    val state by rememberPhoto(url)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), Alignment.Center) {
        when (val current = state) {
            is PhotoState.Ready -> {
                val alpha by animateFloatAsState(1f, label = "photoFade")
                Image(
                    bitmap = current.bitmap,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    alpha = alpha,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            PhotoState.Loading -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.outline,
            )

            PhotoState.Failed -> Icon(
                imageVector = Icons.Outlined.BrokenImage,
                contentDescription = "Image indisponible",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
