package com.florapin.desktop.map

import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/** Côté d'une tuile MapTiler, en pixels. */
const val TILE_SIZE = 256.0

/**
 * Projection Web Mercator (EPSG:3857), celle qu'emploient toutes les tuiles
 * raster. Les coordonnées « monde » sont exprimées en pixels au niveau de zoom
 * considéré : à z, la Terre occupe `256 · 2^z` pixels de côté.
 */
object MapMath {

    /** Latitude maximale représentable : au-delà, Mercator diverge. */
    const val MAX_LATITUDE = 85.05112878

    fun lonToWorldX(lon: Double, zoom: Int): Double =
        (lon + 180.0) / 360.0 * worldSize(zoom)

    fun latToWorldY(lat: Double, zoom: Int): Double {
        val clamped = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val rad = clamped * PI / 180.0
        val y = ln(tan(rad) + 1.0 / kotlin.math.cos(rad))
        // Le bornage final n'est pas redondant avec celui de la latitude : aux
        // limites, l'arrondi flottant produit un résultat très légèrement hors
        // du monde (de l'ordre de 1e-8 pixel). Une valeur négative ferait
        // tomber la tuile calculée en -1, donc écarter du rendu un marqueur
        // situé tout au nord.
        return ((1.0 - y / PI) / 2.0 * worldSize(zoom)).coerceIn(0.0, worldSize(zoom))
    }

    fun worldXToLon(x: Double, zoom: Int): Double =
        x / worldSize(zoom) * 360.0 - 180.0

    fun worldYToLat(y: Double, zoom: Int): Double {
        val n = PI * (1.0 - 2.0 * y / worldSize(zoom))
        return 180.0 / PI * atan(sinh(n))
    }

    fun worldSize(zoom: Int): Double = TILE_SIZE * (1 shl zoom)

    /**
     * Zoom entier permettant d'englober une zone dans une fenêtre donnée.
     * Utilisé au premier affichage pour cadrer sur l'ensemble des photos.
     */
    fun zoomToFit(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        widthPx: Double,
        heightPx: Double,
        maxZoom: Int = 16,
    ): Int {
        if (widthPx <= 0 || heightPx <= 0) return 2
        // Fractions du monde couvertes par la zone, indépendantes du zoom.
        val lonFraction = ((maxLon - minLon) / 360.0).coerceAtLeast(1e-9)
        val latFraction = ((mercatorY(maxLat) - mercatorY(minLat)) / 2.0).coerceAtLeast(1e-9)
        val zoomForWidth = ln(widthPx / TILE_SIZE / lonFraction) / ln(2.0)
        val zoomForHeight = ln(heightPx / TILE_SIZE / latFraction) / ln(2.0)
        return floor(minOf(zoomForWidth, zoomForHeight)).toInt().coerceIn(1, maxZoom)
    }

    /** Ordonnée Mercator normalisée dans [-1, 1] — sert au cadrage. */
    private fun mercatorY(lat: Double): Double {
        val clamped = lat.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        return asinh(tan(clamped * PI / 180.0)) / PI
    }
}
