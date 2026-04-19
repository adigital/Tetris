package ru.adigital.tetris.vision

import androidx.annotation.VisibleForTesting
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Эвристики для грубой классификации сетки по средней яркости ячеек (без ML).
 */
object VisionGridHeuristics {

    /**
     * Доля обрезки с края ячейки: для очень мелких ячеек (4×4 на маленьком ROI) — меньше,
     * чтобы не «съесть» пиксели фигуры; для крупных — сильнее убрать линии LCD.
     */
    @VisibleForTesting
    fun trimFractionForCellSpan(minCellSpanPx: Int): Float =
        when {
            minCellSpanPx <= VisionTuning.CELL_TRIM_SPAN_THRESHOLD_1 -> VisionTuning.CELL_TRIM_MIN_SPAN_PX_LE5
            minCellSpanPx <= VisionTuning.CELL_TRIM_SPAN_THRESHOLD_2 -> VisionTuning.CELL_TRIM_MIN_SPAN_PX_LE10
            minCellSpanPx <= VisionTuning.CELL_TRIM_SPAN_THRESHOLD_3 -> VisionTuning.CELL_TRIM_MIN_SPAN_PX_LE18
            else -> VisionTuning.CELL_TRIM_MIN_SPAN_PX_DEFAULT
        }

    /**
     * Средняя яркость 0..255 по **центральной** части каждой ячейки (без краёв, где линии сетки).
     */
    fun cellMeans(frame: GrayscaleRoiFrame, columns: Int, rows: Int): FloatArray {
        require(columns > 0 && rows > 0)
        val cw = frame.width / columns
        val ch = frame.height / rows
        require(cw > 0 && ch > 0) { "ROI слишком мал для сетки ${columns}x$rows" }

        val trimFr = trimFractionForCellSpan(min(cw, ch))
        val insetX = (cw * trimFr).toInt().coerceIn(0, (cw - 1).coerceAtLeast(0) / 2)
        val insetY = (ch * trimFr).toInt().coerceIn(0, (ch - 1).coerceAtLeast(0) / 2)

        val out = FloatArray(columns * rows)
        val lum = frame.luminance
        val w = frame.width
        var idx = 0
        for (ry in 0 until rows) {
            val y0 = ry * ch
            val y1 = y0 + ch
            for (cx in 0 until columns) {
                val x0 = cx * cw
                val x1 = x0 + cw
                var xs = x0 + insetX
                var xe = x1 - insetX
                if (xe <= xs) {
                    xs = x0
                    xe = x1
                }
                var ys = y0 + insetY
                var ye = y1 - insetY
                if (ye <= ys) {
                    ys = y0
                    ye = y1
                }
                var sum = 0L
                var n = 0
                for (y in ys until ye) {
                    val row = y * w
                    for (x in xs until xe) {
                        sum += lum[row + x].toInt() and 0xFF
                        n++
                    }
                }
                out[idx++] = sum.toFloat() / n
            }
        }
        return out
    }

    /**
     * Классификация ячеек: Оцу по средним ячейкам + полярность по близости углов к кластерам
     * (углы стакана обычно пустые) + инверсия, если «заполнено» нереально много при пустых углах.
     */
    fun classifyMeans(
        means: FloatArray,
        columns: Int,
        rows: Int,
    ): Pair<List<CellKind>, Float> {
        if (means.isEmpty()) {
            return List(columns * rows) { CellKind.Unknown } to 0f
        }
        if (columns * rows <= VisionTuning.SMALL_GRID_MAX_CELLS) {
            return classifySmallGridByBorderRef(means, columns, rows)
        }
        val samples = IntArray(means.size) { i -> means[i].roundToInt().coerceIn(0, 255) }
        val threshold = otsuThreshold(samples) ?: return List(columns * rows) { CellKind.Unknown } to 0f
        val boundary = threshold.toFloat() + VisionTuning.OTSU_BOUNDARY_BIAS

        val below = means.filter { it < boundary }
        val above = means.filter { it >= boundary }
        if (below.isEmpty() || above.isEmpty()) {
            return List(columns * rows) { CellKind.Unknown } to 0f
        }

        val meanBelow = below.average().toFloat()
        val meanAbove = above.average().toFloat()
        val spread = abs(meanAbove - meanBelow)
        if (spread < VisionTuning.OTSU_MIN_SPREAD) {
            return List(columns * rows) { CellKind.Unknown } to
                (spread / VisionTuning.OTSU_MIN_SPREAD).coerceIn(0f, 1f)
        }

        val filledIsDarker = filledIsDarkerFromCorners(
            means = means,
            columns = columns,
            rows = rows,
            meanBelow = meanBelow,
            meanAbove = meanAbove,
        )
        var cells = means.map { m ->
            val filled = if (filledIsDarker) m < boundary else m >= boundary
            if (filled) CellKind.Filled else CellKind.Empty
        }
        if (shouldInvertDominatedWallMisread(cells, columns, rows) ||
            shouldInvertBrightCornersDenseFill(means, cells, columns, rows)
        ) {
            cells = invertFilledEmpty(cells)
        }
        if (columns * rows >= VisionTuning.LARGE_GRID_MIN_CELLS) {
            val mut = removeLoneFilledSpeckles(cells, columns, rows).toMutableList()
            stripFalseHudTopRowLargeGrid(mut, means, columns, rows)
            cells = mut
        }
        val confidence = (spread / VisionTuning.CONFIDENCE_SPREAD_DIVISOR).coerceIn(
            VisionTuning.CONFIDENCE_MIN,
            VisionTuning.CONFIDENCE_MAX,
        )
        return cells to confidence
    }

    /**
     * Стакан 10×20: если **вся верхняя строка** «занята», а по яркости она как **ряды 1–3** (HUD/рамка),
     * сбрасываем верхнюю строку. Нижнюю не трогаем — там может быть реальный штабель.
     */
    internal fun stripFalseHudTopRowLargeGrid(
        cells: MutableList<CellKind>,
        means: FloatArray,
        columns: Int,
        rows: Int,
    ) {
        if (columns * rows < VisionTuning.LARGE_GRID_MIN_CELLS || rows < VisionTuning.HUD_MIN_ROWS) return
        if (!(0 until columns).all { cells[it] == CellKind.Filled }) return

        val totalFilled = cells.count { it == CellKind.Filled }
        // Только одна верхняя линия «занята» — почти всегда ложный HUD/рамка, не фигура
        if (totalFilled <= columns) {
            for (c in 0 until columns) {
                cells[c] = CellKind.Empty
            }
            return
        }

        val topMean = (0 until columns).map { means[it] }.average().toFloat()
        val interior = ArrayList<Float>(columns * 3)
        for (ry in 1..VisionTuning.HUD_REFERENCE_ROW_COUNT) {
            for (cx in 0 until columns) {
                interior.add(means[ry * columns + cx])
            }
        }
        val sorted = interior.sorted()
        val ref = (sorted[sorted.size / 2] + sorted[(sorted.size - 1) / 2]) / 2f
        val mn = means.minOrNull()!!
        val mx = means.maxOrNull()!!
        val span = (mx - mn).coerceAtLeast(1f)
        val tol = (VisionTuning.HUD_TOP_VS_INTERIOR_TOL_FRAC * span).coerceAtLeast(VisionTuning.HUD_TOP_VS_INTERIOR_TOL_MIN)
        if (abs(topMean - ref) < tol) {
            for (c in 0 until columns) {
                cells[c] = CellKind.Empty
            }
        }
    }

    private fun invertFilledEmpty(cells: List<CellKind>): List<CellKind> =
        cells.map { c ->
            when (c) {
                CellKind.Filled -> CellKind.Empty
                CellKind.Empty -> CellKind.Filled
                CellKind.Unknown -> c
            }
        }

    /**
     * Малая сетка (окно next 4×4 и т.п.): фон по **периметру** (часто пустой), блоки как отклонение
     * тёмнее или светлее фона — без Оцу по 16 точкам (часто даёт ложную уверенность).
     */
    private fun classifySmallGridByBorderRef(
        means: FloatArray,
        columns: Int,
        rows: Int,
    ): Pair<List<CellKind>, Float> {
        val border = borderCellIndices(columns, rows).map { means[it] }
        if (border.isEmpty()) {
            return List(means.size) { CellKind.Unknown } to 0f
        }
        val sortedB = border.sorted()
        val emptyRef = (sortedB[sortedB.size / 2] + sortedB[(sortedB.size - 1) / 2]) / 2f
        val mn = means.minOrNull()!!
        val mx = means.maxOrNull()!!
        val span = mx - mn
        if (span < VisionTuning.SMALL_GRID_MIN_SPAN) {
            return List(means.size) { CellKind.Unknown } to
                (span / VisionTuning.SMALL_GRID_MIN_SPAN).coerceIn(
                    VisionTuning.SMALL_GRID_LOW_CONFIDENCE_FLOOR,
                    VisionTuning.SMALL_GRID_LOW_CONFIDENCE_CAP,
                )
        }
        val k = VisionTuning.SMALL_GRID_BORDER_K_FRAC * span + VisionTuning.SMALL_GRID_BORDER_K_ADD

        fun buildDarkBlocks(): List<CellKind> = means.map { m ->
            if (emptyRef - m > k) CellKind.Filled else CellKind.Empty
        }
        fun buildLightBlocks(): List<CellKind> = means.map { m ->
            if (m - emptyRef > k) CellKind.Filled else CellKind.Empty
        }

        val dark = buildDarkBlocks()
        val light = buildLightBlocks()
        val cd = dark.count { it == CellKind.Filled }
        val cl = light.count { it == CellKind.Filled }

        fun cornersFilled(c: List<CellKind>): Int {
            val ci = cornerCellIndices(columns, rows)
            return ci.count { c[it] == CellKind.Filled }
        }

        val cells = when {
            cd in VisionTuning.SMALL_GRID_FILLED_COUNT_MIN..VisionTuning.SMALL_GRID_FILLED_COUNT_MAX &&
                cl in VisionTuning.SMALL_GRID_FILLED_COUNT_MIN..VisionTuning.SMALL_GRID_FILLED_COUNT_MAX ->
                if (cornersFilled(dark) <= cornersFilled(light)) dark else light
            cd in VisionTuning.SMALL_GRID_FILLED_COUNT_MIN..VisionTuning.SMALL_GRID_FILLED_COUNT_MAX -> dark
            cl in VisionTuning.SMALL_GRID_FILLED_COUNT_MIN..VisionTuning.SMALL_GRID_FILLED_COUNT_MAX -> light
            cd == 0 && cl == 0 -> List(means.size) { CellKind.Unknown }
            else -> if (cd <= cl) dark else light
        }
        if (cells.all { it == CellKind.Unknown }) {
            return cells to VisionTuning.SMALL_GRID_UNKNOWN_CONFIDENCE
        }
        var outMut = cells.toMutableList()
        val ratio = outMut.count { it == CellKind.Filled }.toFloat() / means.size
        if (ratio > VisionTuning.SMALL_GRID_INVERT_IF_FILLED_RATIO) {
            outMut = invertFilledEmpty(outMut).toMutableList()
        }
        if (columns == VisionTuning.NEXT_PREVIEW_GRID_SIZE && rows == VisionTuning.NEXT_PREVIEW_GRID_SIZE) {
            stripFalseBezelEdgeLineOnSmallGrid(outMut, means, columns, rows, emptyRef, span)
        }
        val confidence = (span / VisionTuning.SMALL_GRID_CONFIDENCE_DIVISOR).coerceIn(
            VisionTuning.SMALL_GRID_CONFIDENCE_MIN,
            VisionTuning.CONFIDENCE_MAX,
        )
        return outMut to confidence
    }

    /**
     * Для 4×4: полный крайний столбец/строка «блоков» при яркости как у фона — часто рамка LCD в ROI, сбрасываем.
     */
    internal fun stripFalseBezelEdgeLineOnSmallGrid(
        cells: MutableList<CellKind>,
        means: FloatArray,
        columns: Int,
        rows: Int,
        emptyRef: Float,
        span: Float,
    ) {
        if (columns != VisionTuning.NEXT_PREVIEW_GRID_SIZE || rows != VisionTuning.NEXT_PREVIEW_GRID_SIZE) return
        val tol = (VisionTuning.BEZEL_4X4_TOL_FRAC * span).coerceAtLeast(VisionTuning.BEZEL_4X4_TOL_MIN)

        fun colFilled(c: Int) = (0 until rows).all { cells[it * columns + c] == CellKind.Filled }
        fun colMean(c: Int) = (0 until rows).map { means[it * columns + c] }.average().toFloat()
        fun rowFilled(r: Int) = (0 until columns).all { cells[r * columns + it] == CellKind.Filled }
        fun rowMean(r: Int) = (0 until columns).map { means[r * columns + it] }.average().toFloat()

        for (c in listOf(0, columns - 1)) {
            if (!colFilled(c)) continue
            if (abs(colMean(c) - emptyRef) < tol) {
                for (r in 0 until rows) {
                    cells[r * columns + c] = CellKind.Empty
                }
            }
        }
        for (r in listOf(0, rows - 1)) {
            if (!rowFilled(r)) continue
            if (abs(rowMean(r) - emptyRef) < tol) {
                for (c in 0 until columns) {
                    cells[r * columns + c] = CellKind.Empty
                }
            }
        }
    }

    /** Индексы клеток по периметру (для оценки фона next-окна). */
    @VisibleForTesting
    fun borderCellIndices(columns: Int, rows: Int): List<Int> {
        val idx = mutableSetOf<Int>()
        for (x in 0 until columns) {
            idx.add(x)
            if (rows > 1) idx.add((rows - 1) * columns + x)
        }
        for (y in 1 until rows - 1) {
            idx.add(y * columns)
            idx.add(y * columns + columns - 1)
        }
        return idx.toList()
    }

    /** Одиночные «блоки» без соседей по сторонам — шум после порога (только крупная сетка). */
    private fun removeLoneFilledSpeckles(
        cells: List<CellKind>,
        columns: Int,
        rows: Int,
    ): List<CellKind> {
        val out = cells.toMutableList()
        fun filledAt(ix: Int, iy: Int): Boolean {
            if (ix < 0 || iy < 0 || ix >= columns || iy >= rows) return false
            return out[iy * columns + ix] == CellKind.Filled
        }
        for (iy in 0 until rows) {
            for (ix in 0 until columns) {
                val i = iy * columns + ix
                if (out[i] != CellKind.Filled) continue
                val neighbors = listOf(
                    filledAt(ix - 1, iy),
                    filledAt(ix + 1, iy),
                    filledAt(ix, iy - 1),
                    filledAt(ix, iy + 1),
                ).count { it }
                if (neighbors == 0) {
                    out[i] = CellKind.Empty
                }
            }
        }
        return out
    }

    /**
     * Углы сетки в row-major: (0,0), (0,cols-1), (rows-1,0), (rows-1, cols-1).
     */
    @VisibleForTesting
    fun cornerCellIndices(columns: Int, rows: Int): IntArray =
        intArrayOf(
            0,
            columns - 1,
            (rows - 1) * columns,
            rows * columns - 1,
        )

    /** Кластер с блоками тёмнее пустых клеток? Сначала углы ≈ пустой фон; при противоречии — как у Оцу по яркости. */
    private fun filledIsDarkerFromCorners(
        means: FloatArray,
        columns: Int,
        rows: Int,
        meanBelow: Float,
        meanAbove: Float,
    ): Boolean {
        if (columns >= VisionTuning.MIN_GRID_DIM_FOR_TOP_ROW_CORNER_POLARITY &&
            rows >= VisionTuning.MIN_GRID_DIM_FOR_TOP_ROW_CORNER_POLARITY
        ) {
            val topL = means[0]
            val topR = means[columns - 1]
            // Верхние углы сильно разные (фигура цепляет верх) — не выводим «пустоту» по углам
            if (abs(topL - topR) > VisionTuning.TOP_ROW_CORNER_LUMA_DIFF_FOR_FALLBACK) {
                return meanBelow < meanAbove
            }
        }
        val idx = cornerCellIndices(columns, rows)
        val cm = idx.map { means[it] }.sorted()
        val cornerMed = (cm[1] + cm[2]) / 2f
        val spreadCorners = cm[3] - cm[0]
        if (spreadCorners > VisionTuning.CORNER_LUMA_SPREAD_FOR_FALLBACK) {
            return meanBelow < meanAbove
        }
        val distBelow = abs(meanBelow - cornerMed)
        val distAbove = abs(meanAbove - cornerMed)
        val emptyIsBelowCluster = distBelow <= distAbove
        val emptyMean = if (emptyIsBelowCluster) meanBelow else meanAbove
        val filledMean = if (emptyIsBelowCluster) meanAbove else meanBelow
        return filledMean < emptyMean
    }

    /**
     * Если почти всё «занято», а 3+ угла тоже «заняты» — типичная инверсия фона/блоков на LCD.
     */
    @VisibleForTesting
    fun shouldInvertDominatedWallMisread(
        cells: List<CellKind>,
        columns: Int,
        rows: Int,
    ): Boolean {
        val total = columns * rows
        val filled = cells.count { it == CellKind.Filled }
        val ratio = filled.toFloat() / total
        val corners = cornerCellIndices(columns, rows)
        val cornersFilled = corners.count { cells[it] == CellKind.Filled }
        val largeWell = total >= VisionTuning.LARGE_GRID_MIN_CELLS
        val ratioThreshold = if (largeWell) {
            VisionTuning.INVERT_LARGE_RATIO_TYPE_A
        } else {
            VisionTuning.INVERT_SMALL_RATIO_TYPE_A
        }
        val typeA = ratio > ratioThreshold && cornersFilled >= VisionTuning.INVERT_TYPE_A_MIN_CORNERS_FILLED
        // Почти всё «занято», углов мало «занятых» — часто 1–2 угла ошибочно Filled при заливе
        val typeB = largeWell && ratio > VisionTuning.INVERT_TYPE_B_RATIO &&
            cornersFilled <= VisionTuning.INVERT_TYPE_B_MAX_CORNERS_FILLED
        return typeA || typeB
    }

    /**
     * Много клеток помечено как Filled, но по **яркости** углы в верхней части диапазона —
     * как пустой LCD по углам при перевёрнутой полярности.
     */
    @VisibleForTesting
    fun shouldInvertBrightCornersDenseFill(
        means: FloatArray,
        cells: List<CellKind>,
        columns: Int,
        rows: Int,
    ): Boolean {
        if (columns * rows < VisionTuning.LARGE_GRID_MIN_CELLS) return false
        val ratio = cells.count { it == CellKind.Filled }.toFloat() / cells.size
        if (ratio < VisionTuning.INVERT_BRIGHT_CORNERS_MIN_FILLED_RATIO) return false
        val ci = cornerCellIndices(columns, rows)
        val cornerMean = ci.map { means[it] }.average().toFloat()
        val mn = means.minOrNull()!!
        val mx = means.maxOrNull()!!
        val span = (mx - mn).coerceAtLeast(1f)
        return (cornerMean - mn) / span >= VisionTuning.INVERT_BRIGHT_CORNERS_MIN_NORMALIZED
    }

    /**
     * Порог Оцу для 8-битных значений (гистограмма 256 бинов).
     */
    @VisibleForTesting
    fun otsuThreshold(samples: IntArray): Int? {
        val hist = IntArray(256)
        for (v in samples) hist[v]++
        val total = samples.size
        if (total == 0) return null

        var sumAll = 0.0
        for (t in 0 until 256) sumAll += t * hist[t]

        var sumB = 0.0
        var wB = 0
        var maxVar = -1.0
        var threshold = -1

        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += t * hist[t]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                threshold = t
            }
        }
        return if (threshold >= 0) threshold else null
    }
}
