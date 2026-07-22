package com.florapin.app.capture

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Réduction Lanczos3 séparable, utilisée uniquement dans le Worker photo. */
object LanczosBitmapScaler {
    private const val LOBES = 3.0

    fun scaleInside(source: Bitmap, maxEdge: Int): Bitmap {
        val edge = max(source.width, source.height)
        if (edge <= maxEdge) return source
        val ratio = maxEdge.toDouble() / edge
        val targetWidth = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val targetHeight = (source.height * ratio).roundToInt().coerceAtLeast(1)
        return resize(source, targetWidth, targetHeight)
    }

    private fun resize(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val sourcePixels = IntArray(source.width * source.height)
        source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)

        val horizontalWeights = weights(source.width, targetWidth)
        val horizontal = IntArray(targetWidth * source.height)
        for (y in 0 until source.height) {
            val sourceRow = y * source.width
            val targetRow = y * targetWidth
            for (x in 0 until targetWidth) {
                horizontal[targetRow + x] = sample(
                    sourcePixels,
                    sourceRow,
                    horizontalWeights.indices[x],
                    horizontalWeights.values[x],
                )
            }
        }

        val verticalWeights = weights(source.height, targetHeight)
        val outputPixels = IntArray(targetWidth * targetHeight)
        for (y in 0 until targetHeight) {
            val indices = verticalWeights.indices[y]
            val values = verticalWeights.values[y]
            val targetRow = y * targetWidth
            for (x in 0 until targetWidth) {
                outputPixels[targetRow + x] = sampleRows(horizontal, targetWidth, x, indices, values)
            }
        }

        return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).apply {
            setPixels(outputPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        }
    }

    private fun sample(
        pixels: IntArray,
        rowOffset: Int,
        indices: IntArray,
        weights: DoubleArray,
    ): Int {
        var alpha = 0.0
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        for (tap in indices.indices) {
            val color = pixels[rowOffset + indices[tap]]
            val weight = weights[tap]
            alpha += ((color ushr 24) and 0xff) * weight
            red += ((color ushr 16) and 0xff) * weight
            green += ((color ushr 8) and 0xff) * weight
            blue += (color and 0xff) * weight
        }
        return pack(alpha, red, green, blue)
    }

    private fun sampleRows(
        pixels: IntArray,
        rowWidth: Int,
        x: Int,
        indices: IntArray,
        weights: DoubleArray,
    ): Int {
        var alpha = 0.0
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        for (tap in indices.indices) {
            val color = pixels[indices[tap] * rowWidth + x]
            val weight = weights[tap]
            alpha += ((color ushr 24) and 0xff) * weight
            red += ((color ushr 16) and 0xff) * weight
            green += ((color ushr 8) and 0xff) * weight
            blue += (color and 0xff) * weight
        }
        return pack(alpha, red, green, blue)
    }

    private fun pack(alpha: Double, red: Double, green: Double, blue: Double): Int =
        (alpha.roundToInt().coerceIn(0, 255) shl 24) or
            (red.roundToInt().coerceIn(0, 255) shl 16) or
            (green.roundToInt().coerceIn(0, 255) shl 8) or
            blue.roundToInt().coerceIn(0, 255)

    private fun weights(sourceSize: Int, targetSize: Int): AxisWeights {
        val scale = targetSize.toDouble() / sourceSize
        val filterScale = scale.coerceAtMost(1.0)
        val radius = LOBES / filterScale
        val allIndices = Array(targetSize) { IntArray(0) }
        val allValues = Array(targetSize) { DoubleArray(0) }
        for (target in 0 until targetSize) {
            val center = (target + 0.5) / scale - 0.5
            val left = ceil(center - radius).toInt()
            val right = floor(center + radius).toInt()
            val indices = IntArray(right - left + 1)
            val values = DoubleArray(indices.size)
            var total = 0.0
            for (tap in indices.indices) {
                val source = left + tap
                val value = lanczos((source - center) * filterScale)
                indices[tap] = source.coerceIn(0, sourceSize - 1)
                values[tap] = value
                total += value
            }
            if (abs(total) > 1e-12) {
                for (tap in values.indices) values[tap] /= total
            }
            allIndices[target] = indices
            allValues[target] = values
        }
        return AxisWeights(allIndices, allValues)
    }

    private fun lanczos(value: Double): Double {
        val x = abs(value)
        if (x < 1e-12) return 1.0
        if (x >= LOBES) return 0.0
        val pix = PI * x
        return (sin(pix) / pix) * (sin(pix / LOBES) / (pix / LOBES))
    }

    private data class AxisWeights(
        val indices: Array<IntArray>,
        val values: Array<DoubleArray>,
    )
}
