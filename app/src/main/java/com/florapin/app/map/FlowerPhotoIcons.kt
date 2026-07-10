package com.florapin.app.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Zoom à partir duquel les fleurs isolées s'affichent en pastille photo plutôt
 * qu'en emoji d'espèce. Au-delà du zoom de clusterisation (14) : on ne dépense
 * de vignettes que sur des points déjà séparés les uns des autres.
 */
const val PHOTO_ICON_MIN_ZOOM = 16f

/**
 * Côté de la bulle photo source. Son placement est calculé séparément en
 * coordonnées écran par l'algorithme de répulsion.
 */
const val PHOTO_ICON_SIZE_PX = 192

/** Épaisseur du liseré blanc qui détache la bulle du fond de carte. */
private const val ICON_BORDER_PX = 8f

/** Liseré chaud distinctif des fleurs provenant des amis. */
const val FRIEND_PHOTO_BORDER_COLOR: Int = -22963 // #FFA64D

/**
 * Plafond de vignettes chargées en une passe. Chaque pastille est un bitmap
 * conservé par le style MapLibre : au-delà, la mémoire et le temps de décodage
 * l'emportent sur le gain visuel, et l'on reste sur les emojis.
 */
const val MAX_PHOTO_ICONS = 80

/** Nom de l'image enregistrée dans le style pour la fleur [markerId]. */
fun photoIconId(markerId: Long, friend: Boolean = false): String =
    "flower-photo-${if (friend) "friend" else "mine"}-$markerId"

/**
 * Cale la densité du bitmap sur celle de l'écran. MapLibre en déduit le ratio
 * `densité / 160` qu'il applique à l'inverse au rendu : l'image occupe alors
 * exactement sa largeur en pixels physiques. Sans cela, la taille rendue
 * dépendrait de l'appareil, et le calcul de chevauchement — mené en pixels
 * écran — serait faux.
 */
fun Bitmap.withScreenDensity(context: Context): Bitmap = apply {
    density = context.resources.displayMetrics.densityDpi
}

/**
 * Décode [model] et fabrique la bulle photo ronde. L'emoji et la ligne sont des
 * couches MapLibre distinctes, ce qui permet de déplacer la photo dynamiquement.
 */
suspend fun loadCircularIcon(
    context: Context,
    model: Any?,
    borderColor: Int = Color.WHITE,
): Bitmap? {
    if (model == null) return null
    val request = ImageRequest.Builder(context)
        .data(model)
        .size(PHOTO_ICON_SIZE_PX, PHOTO_ICON_SIZE_PX)
        .allowHardware(false) // Un bitmap matériel n'est pas lisible par Canvas.
        .build()
    val result = context.imageLoader.execute(request)
    if (result !is SuccessResult) return null
    return runCatching { result.drawable.toBitmap().toCircle(context, borderColor) }.getOrNull()
}

/** Recadre au centre, arrondit en cercle et ajoute le liseré. */
private fun Bitmap.toCircle(context: Context, borderColor: Int): Bitmap {
    val output = Bitmap.createBitmap(
        PHOTO_ICON_SIZE_PX,
        PHOTO_ICON_SIZE_PX,
        Bitmap.Config.ARGB_8888,
    ).withScreenDensity(context)
    val canvas = Canvas(output)
    val radius = PHOTO_ICON_SIZE_PX / 2f

    // Le shader échantillonne le bitmap source : on le cadre pour que le plus
    // petit côté remplisse la pastille (équivalent d'un ContentScale.Crop).
    val scale = PHOTO_ICON_SIZE_PX.toFloat() / minOf(width, height)
    val matrix = Matrix().apply {
        setScale(scale, scale)
        postTranslate(
            (PHOTO_ICON_SIZE_PX - width * scale) / 2f,
            (PHOTO_ICON_SIZE_PX - height * scale) / 2f,
        )
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(this@toCircle, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { setLocalMatrix(matrix) }
    }
    canvas.drawCircle(radius, radius, radius - ICON_BORDER_PX / 2f, fill)

    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ICON_BORDER_PX
        color = borderColor
    }
    canvas.drawCircle(radius, radius, radius - ICON_BORDER_PX / 2f, border)
    return output
}
