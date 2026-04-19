package ru.adigital.tetris.vision

/**
 * Состояние клетки игрового поля (упрощённо, для будущего CV).
 */
enum class CellKind {
    Unknown,
    Empty,
    Filled,
}

/**
 * Снимок поля с камеры: сетка + метаданные уверенности.
 */
data class PlayfieldSnapshot(
    val columns: Int,
    val rows: Int,
    val cells: List<CellKind>,
    val frameTimestampNanos: Long,
    val confidence: Float,
) {
    init {
        require(columns > 0 && rows > 0)
        require(cells.size == columns * rows)
    }

    companion object {
        fun unknownGrid(columns: Int = 10, rows: Int = 20, timestampNanos: Long = 0L): PlayfieldSnapshot =
            PlayfieldSnapshot(
                columns = columns,
                rows = rows,
                cells = List(columns * rows) { CellKind.Unknown },
                frameTimestampNanos = timestampNanos,
                confidence = 0f,
            )
    }
}
