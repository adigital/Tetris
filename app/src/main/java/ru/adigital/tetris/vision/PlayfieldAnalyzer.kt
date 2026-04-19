package ru.adigital.tetris.vision

import androidx.camera.core.ImageProxy

/**
 * Распознавание поля по кадру. Пока возвращает «неизвестную» сетку заданного размера.
 */
fun interface PlayfieldAnalyzer {
    fun analyze(image: ImageProxy, roi: NormalizedRoi): PlayfieldSnapshot
}

object StubPlayfieldAnalyzer : PlayfieldAnalyzer {
    override fun analyze(image: ImageProxy, roi: NormalizedRoi): PlayfieldSnapshot {
        val ts = image.imageInfo.timestamp
        image.close()
        return PlayfieldSnapshot.unknownGrid(
            columns = 10,
            rows = 20,
            timestampNanos = ts,
        )
    }
}
