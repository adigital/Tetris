package ru.adigital.tetris.vision

import android.graphics.ImageFormat
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
     * Плоскость Y из [ImageFormat.YUV_420_888]: обрезка по [roi] в координатах буфера кадра.
     * Закрытие [image] — ответственность вызывающего.
     */
    fun extractGrayscaleRoi(image: ImageProxy, roi: NormalizedRoi): GrayscaleRoiFrame? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val plane = image.planes.getOrNull(0) ?: return null
        val buf = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

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

        return GrayscaleRoiFrame(
            width = outW,
            height = outH,
            luminance = out,
            timestampNanos = image.imageInfo.timestamp,
        )
    }
}
