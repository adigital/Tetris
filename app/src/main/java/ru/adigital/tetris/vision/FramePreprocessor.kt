package ru.adigital.tetris.vision

import androidx.camera.core.ImageProxy

/**
 * Предобработка кадра под конвейер CV: обрезка по ROI, масштаб, бинаризация (позже).
 */
object FramePreprocessor {

    /**
     * Заглушка: фиксирует размер кадра и ROI для отладки; конвертация YUV → матрица — в следующих шагах.
     */
    fun describeFrame(image: ImageProxy, roi: NormalizedRoi): String =
        "frame=${image.width}x${image.height} roi=[${"%.2f".format(roi.left)},${"%.2f".format(roi.top)}.." +
            "${"%.2f".format(roi.right)},${"%.2f".format(roi.bottom)}]"
}
