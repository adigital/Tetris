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
}
