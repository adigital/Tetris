package ru.adigital.tetris.vision

import androidx.annotation.VisibleForTesting
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Эвристики для грубой классификации сетки по средней яркости ячеек (без ML).
 */
object VisionGridHeuristics {

    /**
     * Средняя яркость 0..255 по прямоугольнику внутри [GrayscaleRoiFrame].
     */
    fun cellMeans(frame: GrayscaleRoiFrame, columns: Int, rows: Int): FloatArray {
        require(columns > 0 && rows > 0)
        val cw = frame.width / columns
        val ch = frame.height / rows
        require(cw > 0 && ch > 0) { "ROI слишком мал для сетки ${columns}x$rows" }

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
                var sum = 0L
                var n = 0
                for (y in y0 until y1) {
                    val row = y * w
                    for (x in x0 until x1) {
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
     * Классификация ячеек по контрасту между двумя кластерами (порог Оцу по гистограмме средних ячеек).
     * @return пары (клетки, уверенность 0..1).
     */
    fun classifyMeans(
        means: FloatArray,
        columns: Int,
        rows: Int,
    ): Pair<List<CellKind>, Float> {
        if (means.isEmpty()) {
            return List(columns * rows) { CellKind.Unknown } to 0f
        }
        val samples = IntArray(means.size) { i -> means[i].roundToInt().coerceIn(0, 255) }
        val threshold = otsuThreshold(samples) ?: return List(columns * rows) { CellKind.Unknown } to 0f
        val boundary = threshold.toFloat() + 0.5f

        val below = means.filter { it < boundary }
        val above = means.filter { it >= boundary }
        if (below.isEmpty() || above.isEmpty()) {
            return List(columns * rows) { CellKind.Unknown } to 0f
        }

        val meanBelow = below.average().toFloat()
        val meanAbove = above.average().toFloat()
        val spread = abs(meanAbove - meanBelow)
        if (spread < 6f) {
            return List(columns * rows) { CellKind.Unknown } to (spread / 6f).coerceIn(0f, 1f)
        }

        val filledIsDarker = meanBelow < meanAbove
        val cells = means.map { m ->
            val filled = if (filledIsDarker) m < boundary else m >= boundary
            if (filled) CellKind.Filled else CellKind.Empty
        }
        val confidence = (spread / 48f).coerceIn(0.15f, 1f)
        return cells to confidence
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
