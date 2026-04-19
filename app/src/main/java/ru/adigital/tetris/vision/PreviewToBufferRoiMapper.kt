package ru.adigital.tetris.vision

import android.graphics.Rect
import androidx.camera.core.ImageProxy
import kotlin.math.max
import kotlin.math.min

/**
 * Перевод ROI из нормализованных координат [PreviewView] (как в UI, 0..1 по всей области превью)
 * в прямоугольник обрезки в пикселях буфера [ImageProxy] с учётом [PreviewView.ScaleType.FIT_CENTER]
 * и [androidx.camera.core.ImageInfo.rotationDegrees].
 */
object PreviewToBufferRoiMapper {

    /** Размеры «как на экране» для буфера WxH при заданном повороте метаданных кадра. */
    fun displayOrientedBufferSize(bufferWidth: Int, bufferHeight: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            bufferHeight to bufferWidth
        } else {
            bufferWidth to bufferHeight
        }

    /**
     * Ось-выровненный прямоугольник в координатах буфера [ImageProxy] (left/top включительно,
     * right/bottom — как конец полуинтервала для циклов `until right` / `until bottom`).
     */
    fun bufferCropRectForViewRoi(
        image: ImageProxy,
        roi: NormalizedRoi,
        viewWidthPx: Int,
        viewHeightPx: Int,
    ): Rect {
        if (viewWidthPx <= 0 || viewHeightPx <= 0) {
            return legacyBufferRect(image, roi)
        }
        val bufW = image.width
        val bufH = image.height
        val rot = normalizeRotation(image.imageInfo.rotationDegrees)
        val (dispW, dispH) = displayOrientedBufferSize(bufW, bufH, rot)

        val corners = listOf(
            roi.left to roi.top,
            roi.right to roi.top,
            roi.right to roi.bottom,
            roi.left to roi.bottom,
        )
        var minBx = Int.MAX_VALUE
        var minBy = Int.MAX_VALUE
        var maxBx = Int.MIN_VALUE
        var maxBy = Int.MIN_VALUE
        for ((nx, ny) in corners) {
            val (bx, by) = viewNormalizedToBufferPixel(
                nx = nx,
                ny = ny,
                viewWidthPx = viewWidthPx,
                viewHeightPx = viewHeightPx,
                dispW = dispW,
                dispH = dispH,
                bufW = bufW,
                bufH = bufH,
                rotationDegrees = rot,
            )
            val ix = bx.toInt().coerceIn(0, max(0, bufW - 1))
            val iy = by.toInt().coerceIn(0, max(0, bufH - 1))
            minBx = min(minBx, ix)
            minBy = min(minBy, iy)
            maxBx = max(maxBx, ix)
            maxBy = max(maxBy, iy)
        }

        var left = minBx
        var top = minBy
        var right = (maxBx + 1).coerceAtMost(bufW)
        var bottom = (maxBy + 1).coerceAtMost(bufH)
        if (right <= left) right = min(left + 1, bufW)
        if (bottom <= top) bottom = min(top + 1, bufH)

        val crop = image.cropRect
        left = left.coerceIn(crop.left, crop.right - 1)
        top = top.coerceIn(crop.top, crop.bottom - 1)
        right = right.coerceIn(left + 1, crop.right)
        bottom = bottom.coerceIn(top + 1, crop.bottom)
        return Rect(left, top, right, bottom)
    }

    private fun legacyBufferRect(image: ImageProxy, roi: NormalizedRoi): Rect {
        val imgW = image.width
        val imgH = image.height
        val crop = image.cropRect
        var left = (roi.left * imgW).toInt()
        var top = (roi.top * imgH).toInt()
        var right = (roi.right * imgW).toInt().coerceAtLeast(left + 1)
        var bottom = (roi.bottom * imgH).toInt().coerceAtLeast(top + 1)
        left = left.coerceIn(crop.left, crop.right - 1)
        top = top.coerceIn(crop.top, crop.bottom - 1)
        right = right.coerceIn(left + 1, crop.right)
        bottom = bottom.coerceIn(top + 1, crop.bottom)
        return Rect(left, top, right, bottom)
    }

    /** Для тестов: один угол нормализованного ROI → пиксель в буфере (float, до clamp). */
    internal fun viewNormalizedToBufferPixel(
        nx: Float,
        ny: Float,
        viewWidthPx: Int,
        viewHeightPx: Int,
        dispW: Int,
        dispH: Int,
        bufW: Int,
        bufH: Int,
        rotationDegrees: Int,
    ): Pair<Float, Float> {
        val vw = viewWidthPx.toFloat().coerceAtLeast(1f)
        val vh = viewHeightPx.toFloat().coerceAtLeast(1f)
        val dispWf = dispW.toFloat().coerceAtLeast(1f)
        val dispHf = dispH.toFloat().coerceAtLeast(1f)
        val vx = nx * vw
        val vy = ny * vh
        val scale = min(vw / dispWf, vh / dispHf).coerceAtLeast(1e-6f)
        val imgPixelW = dispWf * scale
        val imgPixelH = dispHf * scale
        val offX = (vw - imgPixelW) * 0.5f
        val offY = (vh - imgPixelH) * 0.5f
        val rx = ((vx - offX) / scale).coerceIn(0f, dispWf - 1e-4f)
        val ry = ((vy - offY) / scale).coerceIn(0f, dispHf - 1e-4f)
        return uprightDisplayPixelToBuffer(rx, ry, bufW, bufH, rotationDegrees)
    }

    /**
     * Пиксель в ориентации «как на превью» (dispW x dispH) → пиксель в буфере [ImageProxy.width] x [ImageProxy.height].
     */
    internal fun uprightDisplayPixelToBuffer(
        rx: Float,
        ry: Float,
        bufW: Int,
        bufH: Int,
        rotationDegrees: Int,
    ): Pair<Float, Float> =
        when (rotationDegrees) {
            0 -> rx to ry
            90 -> ry to (bufH - 1f - rx)
            180 -> (bufW - 1f - rx) to (bufH - 1f - ry)
            270 -> (bufW - 1f - ry) to rx
            else -> rx to ry
        }

    private fun normalizeRotation(deg: Int): Int {
        var d = deg % 360
        if (d < 0) d += 360
        return when (d) {
            0, 90, 180, 270 -> d
            else -> 0
        }
    }
}
