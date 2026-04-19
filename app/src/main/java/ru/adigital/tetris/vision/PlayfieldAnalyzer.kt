package ru.adigital.tetris.vision

import androidx.camera.core.ImageProxy

/**
 * Распознавание поля по кадру.
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

/** Результат анализа основного стакана и окна «следующая» за один кадр. */
data class DualPlayfieldSnapshots(
    val playfield: PlayfieldSnapshot,
    val nextPiece: PlayfieldSnapshot,
)

/**
 * Снимок по открытому [ImageProxy] без [ImageProxy.close] (для нескольких ROI подряд).
 */
internal fun buildSnapshotFromOpenImage(
    image: ImageProxy,
    roi: NormalizedRoi,
    columns: Int,
    rows: Int,
): PlayfieldSnapshot {
    val ts = image.imageInfo.timestamp
    val frame = FramePreprocessor.extractGrayscaleRoi(image, roi)
        ?: return PlayfieldSnapshot.unknownGrid(columns, rows, timestampNanos = ts)
    val means = runCatching { VisionGridHeuristics.cellMeans(frame, columns, rows) }.getOrElse {
        return PlayfieldSnapshot.unknownGrid(columns, rows, timestampNanos = ts)
    }
    val (cells, confidence) = VisionGridHeuristics.classifyMeans(means, columns, rows)
    return PlayfieldSnapshot(
        columns = columns,
        rows = rows,
        cells = cells,
        frameTimestampNanos = ts,
        confidence = confidence,
    )
}

/**
 * Два ROI на одном кадре: стакан 10×20 и превью следующей фигуры 4×4.
 */
object DualPlayfieldAnalyzer {
    fun analyze(
        image: ImageProxy,
        playfieldRoi: NormalizedRoi,
        nextPieceRoi: NormalizedRoi,
    ): DualPlayfieldSnapshots {
        try {
            val playfield = buildSnapshotFromOpenImage(image, playfieldRoi, columns = 10, rows = 20)
            val nextPiece = buildSnapshotFromOpenImage(image, nextPieceRoi, columns = 4, rows = 4)
            return DualPlayfieldSnapshots(playfield = playfield, nextPiece = nextPiece)
        } finally {
            image.close()
        }
    }
}

/**
 * Упрощённый конвейер: Y → ROI → средняя яркость по ячейкам 10×20 → порог Оцу → [Empty]/[Filled].
 */
object HeuristicGridPlayfieldAnalyzer : PlayfieldAnalyzer {
    private const val Columns = 10
    private const val Rows = 20

    override fun analyze(image: ImageProxy, roi: NormalizedRoi): PlayfieldSnapshot {
        try {
            return buildSnapshotFromOpenImage(image, roi, Columns, Rows)
        } finally {
            image.close()
        }
    }
}
