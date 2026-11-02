package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max

data class DocumentQuad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
)

object PerspectiveCorrection {

    /**
     * Default quad covering the full document with a minimal margin (default 1.5%),
     * or exactly 0% for the full edge-to-edge image.
     */
    fun defaultQuad(width: Float, height: Float, marginFraction: Float = 0.015f): DocumentQuad {
        val mx = width * marginFraction
        val my = height * marginFraction
        return DocumentQuad(
            topLeft = PointF(mx, my),
            topRight = PointF(width - mx, my),
            bottomRight = PointF(width - mx, height - my),
            bottomLeft = PointF(mx, height - my)
        )
    }

    fun fullImageQuad(width: Float, height: Float): DocumentQuad {
        return DocumentQuad(
            topLeft = PointF(0f, 0f),
            topRight = PointF(width, 0f),
            bottomRight = PointF(width, height),
            bottomLeft = PointF(0f, height)
        )
    }

    /**
     * Semi-automatic edge & corner detection:
     * Analyzes luminance transitions and contrast boundaries to find paper corners,
     * while allowing the full bounds of the image (no artificial inward clamping).
     */
    fun autoDetectQuad(bitmap: Bitmap): DocumentQuad {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Downsample for fast analysis
        val targetDim = 200
        val scale = max(1f, max(w, h) / targetDim)
        val sw = (w / scale).toInt().coerceAtLeast(10)
        val sh = (h / scale).toInt().coerceAtLeast(10)

        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, false)
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)

        // Calculate average background luminance at four outer edges
        var borderLumSum = 0L
        var borderCount = 0
        for (x in 0 until sw) {
            borderLumSum += getLum(pixels[x])
            borderLumSum += getLum(pixels[(sh - 1) * sw + x])
            borderCount += 2
        }
        for (y in 1 until sh - 1) {
            borderLumSum += getLum(pixels[y * sw])
            borderLumSum += getLum(pixels[y * sw + sw - 1])
            borderCount += 2
        }
        val avgBorderLum = if (borderCount > 0) borderLumSum / borderCount else 80

        // Search inwards from corners along diagonals
        val diagMaxSteps = minOf(sw / 2, sh / 2)
        val threshold = 32 // Significant contrast transition from background to paper

        // Top-Left: search from (0,0) towards center
        var tlStep = 2
        for (i in 0 until diagMaxSteps) {
            val lum = getLum(pixels[i * sw + i])
            if (kotlin.math.abs(lum - avgBorderLum) > threshold) {
                tlStep = i
                break
            }
        }

        // Top-Right: search from (sw-1, 0) towards center
        var trStep = 2
        for (i in 0 until diagMaxSteps) {
            val lum = getLum(pixels[i * sw + (sw - 1 - i)])
            if (kotlin.math.abs(lum - avgBorderLum) > threshold) {
                trStep = i
                break
            }
        }

        // Bottom-Right: search from (sw-1, sh-1) towards center
        var brStep = 2
        for (i in 0 until diagMaxSteps) {
            val lum = getLum(pixels[(sh - 1 - i) * sw + (sw - 1 - i)])
            if (kotlin.math.abs(lum - avgBorderLum) > threshold) {
                brStep = i
                break
            }
        }

        // Bottom-Left: search from (0, sh-1) towards center
        var blStep = 2
        for (i in 0 until diagMaxSteps) {
            val lum = getLum(pixels[(sh - 1 - i) * sw + i])
            if (kotlin.math.abs(lum - avgBorderLum) > threshold) {
                blStep = i
                break
            }
        }

        small.recycle()

        // Convert back to original image coordinates without restricting to center
        val tlX = (tlStep * scale).coerceIn(0f, w * 0.45f)
        val tlY = (tlStep * scale).coerceIn(0f, h * 0.45f)

        val trX = (w - trStep * scale).coerceIn(w * 0.55f, w)
        val trY = (trStep * scale).coerceIn(0f, h * 0.45f)

        val brX = (w - brStep * scale).coerceIn(w * 0.55f, w)
        val brY = (h - brStep * scale).coerceIn(h * 0.55f, h)

        val blX = (blStep * scale).coerceIn(0f, w * 0.45f)
        val blY = (h - blStep * scale).coerceIn(h * 0.55f, h)

        return DocumentQuad(
            topLeft = PointF(tlX, tlY),
            topRight = PointF(trX, trY),
            bottomRight = PointF(brX, brY),
            bottomLeft = PointF(blX, blY)
        )
    }

    private fun getLum(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    /**
     * Performs true 2D perspective orthorectification (polygon to rectangle dewarp).
     */
    fun orthorectify(bitmap: Bitmap, quad: DocumentQuad): Bitmap {
        val topWidth = distance(quad.topLeft, quad.topRight)
        val bottomWidth = distance(quad.bottomLeft, quad.bottomRight)
        val leftHeight = distance(quad.topLeft, quad.bottomLeft)
        val rightHeight = distance(quad.topRight, quad.bottomRight)

        val targetWidth = max(topWidth, bottomWidth).coerceIn(200f, 4000f)
        val targetHeight = max(leftHeight, rightHeight).coerceIn(200f, 5000f)

        val dstBitmap = Bitmap.createBitmap(
            targetWidth.toInt(),
            targetHeight.toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(dstBitmap)

        val matrix = Matrix()
        val src = floatArrayOf(
            quad.topLeft.x, quad.topLeft.y,
            quad.topRight.x, quad.topRight.y,
            quad.bottomRight.x, quad.bottomRight.y,
            quad.bottomLeft.x, quad.bottomLeft.y
        )
        val dst = floatArrayOf(
            0f, 0f,
            targetWidth, 0f,
            targetWidth, targetHeight,
            0f, targetHeight
        )

        matrix.setPolyToPoly(src, 0, dst, 0, 4)

        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
            isDither = true
        }

        canvas.drawBitmap(bitmap, matrix, paint)
        return dstBitmap
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        return hypot(p2.x - p1.x, p2.y - p1.y)
    }
}
