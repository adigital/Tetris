package ru.adigital.tetris.vision

/**
 * Параметры эвристик сетки (яркость / Оцу / инверсии / рамки).
 * Подбирайте под свой LCD, пересобирайте APK. Отдельного экрана настроек пока нет.
 */
@Suppress("MemberVisibilityCanBePrivate")
object VisionTuning {

    // --- Режимы по размеру сетки ---
    /** До этого числа клеток — «малая сетка» (классификация по периметру, типично next 4×4). */
    const val SMALL_GRID_MAX_CELLS = 25

    /** От этого числа клеток — «большой стакан» (спекл, HUD-строка). */
    const val LARGE_GRID_MIN_CELLS = 50

    /** Минимальный размер сетки по стороне для эвристики верхних углов (полярность). */
    const val MIN_GRID_DIM_FOR_TOP_ROW_CORNER_POLARITY = 4

    /** Размер окна «следующая фигура» в клетках (рамка / снятие кромки). */
    const val NEXT_PREVIEW_GRID_SIZE = 4

    // --- Обрезка края ячейки (доля стороны), см. [VisionGridHeuristics.trimFractionForCellSpan] ---
    const val CELL_TRIM_MIN_SPAN_PX_LE5 = 0.06f
    const val CELL_TRIM_MIN_SPAN_PX_LE10 = 0.12f
    const val CELL_TRIM_MIN_SPAN_PX_LE18 = 0.18f
    const val CELL_TRIM_MIN_SPAN_PX_DEFAULT = 0.24f
    const val CELL_TRIM_SPAN_THRESHOLD_1 = 5
    const val CELL_TRIM_SPAN_THRESHOLD_2 = 10
    const val CELL_TRIM_SPAN_THRESHOLD_3 = 18

    // --- Оцу на большой сетке ---
    const val OTSU_BOUNDARY_BIAS = 0.5f
    /** Ниже — почти нет контраста, в Unknown. */
    const val OTSU_MIN_SPREAD = 6f
    /** Делитель для уверенности 0..1 при нормальном разделении кластеров. */
    const val CONFIDENCE_SPREAD_DIVISOR = 48f
    const val CONFIDENCE_MIN = 0.15f
    const val CONFIDENCE_MAX = 1f

    // --- Полярность по углам (filledIsDarkerFromCorners) ---
    const val TOP_ROW_CORNER_LUMA_DIFF_FOR_FALLBACK = 70f
    const val CORNER_LUMA_SPREAD_FOR_FALLBACK = 55f

    // --- Инверсия Empty/Filled (большой стакан) ---
    const val INVERT_LARGE_RATIO_TYPE_A = 0.68f
    const val INVERT_SMALL_RATIO_TYPE_A = 0.82f
    const val INVERT_TYPE_A_MIN_CORNERS_FILLED = 3
    const val INVERT_TYPE_B_RATIO = 0.82f
    const val INVERT_TYPE_B_MAX_CORNERS_FILLED = 2

    const val INVERT_BRIGHT_CORNERS_MIN_FILLED_RATIO = 0.62f
    const val INVERT_BRIGHT_CORNERS_MIN_NORMALIZED = 0.34f

    // --- Верхняя строка HUD (большой стакан) ---
    const val HUD_MIN_ROWS = 8
    const val HUD_REFERENCE_ROW_COUNT = 3
    const val HUD_TOP_VS_INTERIOR_TOL_FRAC = 0.24f
    const val HUD_TOP_VS_INTERIOR_TOL_MIN = 5f

    // --- Малая сетка (периметр + next) ---
    const val SMALL_GRID_MIN_SPAN = 11f
    const val SMALL_GRID_LOW_CONFIDENCE_CAP = 0.9f
    const val SMALL_GRID_LOW_CONFIDENCE_FLOOR = 0.05f
    const val SMALL_GRID_BORDER_K_FRAC = 0.10f
    const val SMALL_GRID_BORDER_K_ADD = 5f
    const val SMALL_GRID_FILLED_COUNT_MIN = 1
    const val SMALL_GRID_FILLED_COUNT_MAX = 10
    const val SMALL_GRID_UNKNOWN_CONFIDENCE = 0.12f
    const val SMALL_GRID_INVERT_IF_FILLED_RATIO = 0.72f
    const val SMALL_GRID_CONFIDENCE_DIVISOR = 42f
    const val SMALL_GRID_CONFIDENCE_MIN = 0.12f

    // --- Снятие ложной кромки 4×4 ---
    const val BEZEL_4X4_TOL_FRAC = 0.28f
    const val BEZEL_4X4_TOL_MIN = 6f
}
