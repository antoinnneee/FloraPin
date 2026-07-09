package com.florapin.app.map

import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/** Un marqueur projeté à l'écran, en pixels. */
data class ScreenPoint(val x: Float, val y: Float)

/**
 * Chevauchement maximal toléré entre deux pastilles voisines, en fraction de
 * leur diamètre. Au-delà, la photo du dessous devient illisible.
 */
private const val MAX_OVERLAP_RATIO = 0.10f

/** Échelle de la pastille à [PHOTO_ICON_MIN_ZOOM], avant plafonnement. */
const val PHOTO_ICON_SCALE_INITIAL = 0.7f

/**
 * Plafond absolu : au-delà, la vignette source ([PHOTO_ICON_SIZE_PX]) est
 * étirée et devient floue.
 */
private const val SCALE_MAX = 1.4f

/**
 * Plancher : sur une grappe très dense, respecter [MAX_OVERLAP_RATIO]
 * demanderait des pastilles invisibles. On préfère un peu de chevauchement.
 */
private const val SCALE_MIN = 0.3f

/** Niveaux de zoom pour un doublement de la taille apparente. */
private const val ZOOM_PER_DOUBLING = 2.0

/**
 * Distance entre les deux marqueurs les plus proches, en pixels écran, ou null
 * s'il y en a moins de deux.
 *
 * Balayage par abscisse croissante : dès que l'écart en x dépasse le meilleur
 * candidat, aucun point suivant ne peut faire mieux.
 */
fun minSeparationPx(points: List<ScreenPoint>): Float? {
    if (points.size < 2) return null
    val sorted = points.sortedBy { it.x }
    var best = Float.MAX_VALUE
    for (i in sorted.indices) {
        for (j in i + 1 until sorted.size) {
            val dx = sorted[j].x - sorted[i].x
            if (dx >= best) break
            val distance = hypot(dx, sorted[j].y - sorted[i].y)
            if (distance < best) best = distance
        }
    }
    return best
}

/**
 * Échelle à appliquer aux pastilles photo : elle croît avec le [zoom]
 * (doublement tous les [ZOOM_PER_DOUBLING] niveaux), sans jamais faire
 * chevaucher deux voisines de plus de [MAX_OVERLAP_RATIO].
 *
 * @param minSeparationPx écart entre les deux marqueurs les plus proches à
 *   l'écran ; null quand il n'y en a qu'un (aucune contrainte de voisinage).
 */
fun photoIconScale(zoom: Double, minSeparationPx: Float?): Float {
    val zoomed = PHOTO_ICON_SCALE_INITIAL *
        2.0.pow((zoom - PHOTO_ICON_MIN_ZOOM) / ZOOM_PER_DOUBLING).toFloat()

    // Deux disques de diamètre d dont les centres sont distants de s se
    // recouvrent de (d - s) / d : borner ce ratio revient à borner d.
    val maxDiameter = minSeparationPx?.div(1f - MAX_OVERLAP_RATIO) ?: Float.MAX_VALUE
    val capped = minOf(zoomed, maxDiameter / PHOTO_ICON_SIZE_PX)
    return capped.coerceIn(SCALE_MIN, SCALE_MAX)
}

/** Vrai si [candidate] s'écarte assez de [current] pour valoir un redessin. */
fun isScaleChangeVisible(current: Float, candidate: Float): Boolean =
    abs(candidate - current) > 0.02f

/**
 * Échelle adaptée à la caméra courante, calculée sur les marqueurs visibles :
 * hors écran, deux fleurs collées ne gênent personne.
 */
fun MapLibreMap.photoIconScaleForCamera(markers: List<FlowerMarker>): Float {
    val bounds = runCatching { projection.visibleRegion.latLngBounds }.getOrNull()
    val visible = markers.mapNotNull { marker ->
        val position = LatLng(marker.latitude, marker.longitude)
        if (bounds != null && !bounds.contains(position)) return@mapNotNull null
        projection.toScreenLocation(position).let { ScreenPoint(it.x, it.y) }
    }
    return photoIconScale(cameraPosition.zoom, minSeparationPx(visible))
}
