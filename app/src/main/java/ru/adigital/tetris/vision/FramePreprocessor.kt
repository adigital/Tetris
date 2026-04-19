package ru.adigital.tetris.vision

import android.graphics.ImageFormat
import android.graphics.Rect
import androidx.camera.core.ImageProxy

/**
 * Предобработка кадра под конвейер CV: обрезка по ROI, яркость из плоскости Y (YUV_420_888).
 */
data class GrayscaleRoiFrame(
    val width: Int,
    val height: Int,
    /** Яркость 0..255, порядок строками (row-major). */
    val luminance: ByteArray,
    val timestampNanos: Long,
) {
    init {
        require(width > 0 && height > 0)
        require(luminance.size == width * height)
    }
}

object FramePreprocessor {

    /**
     * Краткое описание кадра и ROI (для отладки / логов).
     */
    fun describeFrame(image: ImageProxy, roi: NormalizedRoi): String =
        "frame=${image.width}x${image.height} roi=[${"%.2f".format(roi.left)},${"%.2f".format(roi.top)}.." +
            "${"%.2f".format(roi.right)},${"%.2f".format(roi.bottom)}]"

    /**
     * Плоскость Y из [ImageFormat.YUV_420_888]: обрезка по [roi].
     * @param viewWidthPx/viewHeightPx размер [PreviewView] в пикселях; если ≤ 0 — старое поведение (ROI × размер буфера).
     */
    fun extractGrayscaleRoi(
        image: ImageProxy,
        roi: NormalizedRoi,
        viewWidthPx: Int = 0,
        viewHeightPx: Int = 0,
    ): GrayscaleRoiFrame? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val plane = image.planes.getOrNull(0) ?: return null
        val buf = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val rect: Rect = PreviewToBufferRoiMapper.bufferCropRectForViewRoi(
            image = image,
            roi = roi,
            viewWidthPx = viewWidthPx,
            viewHeightPx = viewHeightPx,
        )
        val left = rect.left
        val top = rect.top
        val right = rect.right
        val bottom = rect.bottom

        val outW = right - left
        val outH = bottom - top
        val out = ByteArray(outW * outH)

        var i = 0
        for (y in top until bottom) {
            val rowBase = y * rowStride
            for (x in left until right) {
                out[i++] = buf.get(rowBase + x * pixelStride)
            }
        }

        var frame = GrayscaleRoiFrame(
            width = outW,
            height = outH,
            luminance = out,
            timestampNanos = image.imageInfo.timestamp,
        )
        // Буфер сенсора vs ось «как на превью»: после корректного ROI строки/столбцы яркости
        // соответствуют картинке, повёрнутой на 90° против часовой — выравниваем на 90° по часовой.
        if (viewWidthPx > 0 && viewHeightPx > 0) {
            frame = rotateGrayscale90Clockwise(frame)
        }
        return frame
    }

    /**
     * Поворот изображения яркости на 90° по часовой стрелке (новые размеры height×width).
     */
    internal fun rotateGrayscale90Clockwise(source: GrayscaleRoiFrame): GrayscaleRoiFrame {
        val w = source.width
        val h = source.height
        val src = source.luminance
        val newW = h
        val newH = w
        val dst = ByteArray(newW * newH)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val nx = h - 1 - y
                val ny = x
                dst[ny * newW + nx] = src[row + x]
            }
        }
        return GrayscaleRoiFrame(
            width = newW,
            height = newH,
            luminance = dst,
            timestampNanos = source.timestampNanos,
        )
    }
}
