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
    viewWidthPx: Int = 0,
    viewHeightPx: Int = 0,
): PlayfieldSnapshot {
    val ts = image.imageInfo.timestamp
    val frame = FramePreprocessor.extractGrayscaleRoi(
        image,
        roi,
        viewWidthPx = viewWidthPx,
        viewHeightPx = viewHeightPx,
    )
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
        viewWidthPx: Int = 0,
        viewHeightPx: Int = 0,
    ): DualPlayfieldSnapshots {
        try {
            val playfield = buildSnapshotFromOpenImage(
                image,
                playfieldRoi,
                columns = 10,
                rows = 20,
                viewWidthPx = viewWidthPx,
                viewHeightPx = viewHeightPx,
            )
            val nextPiece = buildSnapshotFromOpenImage(
                image,
                nextPieceRoi,
                columns = 4,
                rows = 4,
                viewWidthPx = viewWidthPx,
                viewHeightPx = viewHeightPx,
            )
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
