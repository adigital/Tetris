package ru.adigital.tetris.vision

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class FramePreprocessorRotationTest {

    @Test
    fun rotate90Cw_twoByTwo() {
        // a b
        // c d  -> CW 90 ->  c a / d b
        val src = GrayscaleRoiFrame(
            width = 2,
            height = 2,
            luminance = byteArrayOf('a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 'd'.code.toByte()),
            timestampNanos = 0L,
        )
        val out = FramePreprocessor.rotateGrayscale90Clockwise(src)
        assertEquals(2, out.width)
        assertEquals(2, out.height)
        assertArrayEquals(
            byteArrayOf('c'.code.toByte(), 'a'.code.toByte(), 'd'.code.toByte(), 'b'.code.toByte()),
            out.luminance,
        )
    }
}
