package ru.adigital.tetris.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionGridHeuristicsTest {

    @Test
    fun otsuThreshold_separatesTwoPeaks() {
        val samples = IntArray(100) { if (it < 40) 40 else 220 }
        val t = VisionGridHeuristics.otsuThreshold(samples)
        assertNotNull(t)
        assertTrue(t!! in 40..220)
    }

    @Test
    fun classifyMeans_darkClusterIsFilled() {
        val columns = 10
        val rows = 20
        val means = FloatArray(columns * rows) { 200f }
        // «Столбик» тёмных блоков слева
        for (r in 0 until rows) {
            means[r * columns] = 40f
            means[r * columns + 1] = 40f
        }
        val (cells, confidence) = VisionGridHeuristics.classifyMeans(means, columns, rows)
        assertTrue(confidence > 0.2f)
        for (r in 0 until rows) {
            assertEquals(CellKind.Filled, cells[r * columns])
            assertEquals(CellKind.Filled, cells[r * columns + 1])
        }
    }

    @Test
    fun shouldInvert_whenAlmostAllFilledAndCornersFilled() {
        val cells = List(200) { CellKind.Filled }
        assertTrue(VisionGridHeuristics.shouldInvertDominatedWallMisread(cells, columns = 10, rows = 20))
    }

    @Test
    fun shouldNotInvert_whenReasonableFill() {
        val columns = 10
        val rows = 20
        val cells = List(columns * rows) { CellKind.Empty }
        assertTrue(!VisionGridHeuristics.shouldInvertDominatedWallMisread(cells, columns, rows))
    }

    @Test
    fun invert_typeB_whenAlmostAllFilledButCornersEmpty() {
        val columns = 10
        val rows = 20
        val cells = MutableList(200) { CellKind.Filled }
        cells[0] = CellKind.Empty
        cells[9] = CellKind.Empty
        cells[190] = CellKind.Empty
        cells[199] = CellKind.Empty
        assertTrue(VisionGridHeuristics.shouldInvertDominatedWallMisread(cells, columns, rows))
    }

    @Test
    fun invert_typeB_whenTwoCornersStillFilled() {
        val columns = 10
        val rows = 20
        val cells = MutableList(200) { CellKind.Filled }
        cells[0] = CellKind.Empty
        cells[9] = CellKind.Empty
        assertTrue(VisionGridHeuristics.shouldInvertDominatedWallMisread(cells, columns, rows))
    }

    @Test
    fun invert_brightCorners_whenDenseFillAndCornersBrightInMeans() {
        val columns = 10
        val rows = 20
        val means = FloatArray(200) { 118f }
        val ci = VisionGridHeuristics.cornerCellIndices(columns, rows)
        for (i in ci) means[i] = 228f
        val cells = List(200) { CellKind.Filled }
        assertTrue(
            VisionGridHeuristics.shouldInvertBrightCornersDenseFill(means, cells, columns, rows),
        )
    }

    @Test
    fun borderCellIndices_4x4_hasTwelveCells() {
        val b = VisionGridHeuristics.borderCellIndices(4, 4)
        assertEquals(12, b.size)
    }

    @Test
    fun stripHud_clearsFullTopRowWhenBrightnessMatchesRowsBelow() {
        val columns = 10
        val rows = 20
        val cells = MutableList(200) { CellKind.Empty }
        for (c in 0 until columns) {
            cells[c] = CellKind.Filled
        }
        val means = FloatArray(200) { 130f }
        for (c in 0 until columns) {
            means[c] = 128f
        }
        VisionGridHeuristics.stripFalseHudTopRowLargeGrid(cells, means, columns, rows)
        for (c in 0 until columns) {
            assertEquals(CellKind.Empty, cells[c])
        }
    }

    @Test
    fun stripHud_clearsTopRowWhenItIsTheOnlyFilledCells_evenIfBrightnessDiffers() {
        val columns = 10
        val rows = 20
        val cells = MutableList(200) { CellKind.Empty }
        for (c in 0 until columns) {
            cells[c] = CellKind.Filled
        }
        val means = FloatArray(200) { 100f }
        for (c in 0 until columns) {
            means[c] = 40f
        }
        for (idx in 10 until 40) {
            means[idx] = 200f
        }
        VisionGridHeuristics.stripFalseHudTopRowLargeGrid(cells, means, columns, rows)
        for (c in 0 until columns) {
            assertEquals(CellKind.Empty, cells[c])
        }
    }

    @Test
    fun stripBezel_clearsFullEdgeColumnWhenBrightnessMatchesBackground() {
        val cells = MutableList(16) { CellKind.Empty }
        for (r in 0 until 4) {
            cells[r * 4 + 3] = CellKind.Filled
        }
        val means = FloatArray(16) { 100f }
        for (r in 0 until 4) {
            means[r * 4 + 3] = 103f
        }
        VisionGridHeuristics.stripFalseBezelEdgeLineOnSmallGrid(
            cells,
            means,
            columns = 4,
            rows = 4,
            emptyRef = 100f,
            span = 80f,
        )
        assertTrue(cells.all { it == CellKind.Empty })
    }

    @Test
    fun cornerCellIndices_fourCorners() {
        val idx = VisionGridHeuristics.cornerCellIndices(10, 20)
        assertEquals(4, idx.size)
        assertEquals(0, idx[0])
        assertEquals(9, idx[1])
        assertEquals(190, idx[2])
        assertEquals(199, idx[3])
    }

    @Test
    fun trimFraction_smallerCellUsesLessTrim() {
        assertTrue(VisionGridHeuristics.trimFractionForCellSpan(4) < VisionGridHeuristics.trimFractionForCellSpan(24))
    }
}
