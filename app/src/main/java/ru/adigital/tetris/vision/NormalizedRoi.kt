package ru.adigital.tetris.vision

/**
 * Область интереса в координатах превью камеры: (0,0) — левый верх, (1,1) — правый низ.
 */
data class NormalizedRoi(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    companion object {
        val Default: NormalizedRoi = fromRaw(0.12f, 0.18f, 0.88f, 0.82f)

        fun fromRaw(left: Float, top: Float, right: Float, bottom: Float): NormalizedRoi {
            var l = left.coerceIn(0f, 1f)
            var t = top.coerceIn(0f, 1f)
            var r = right.coerceIn(0f, 1f)
            var b = bottom.coerceIn(0f, 1f)
            if (r <= l + 0.02f) r = (l + 0.02f).coerceAtMost(1f)
            if (b <= t + 0.02f) b = (t + 0.02f).coerceAtMost(1f)
            if (l >= r - 0.02f) l = (r - 0.02f).coerceAtLeast(0f)
            if (t >= b - 0.02f) t = (b - 0.02f).coerceAtLeast(0f)
            return NormalizedRoi(l, t, r, b)
        }
    }
}
