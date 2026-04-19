package ru.adigital.tetris.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewToBufferRoiMapperTest {

    @Test
    fun displayOrientedBufferSize_rot90_swaps() {
        val (dw, dh) = PreviewToBufferRoiMapper.displayOrientedBufferSize(1920, 1080, 90)
        assertEquals(1080, dw)
        assertEquals(1920, dh)
    }

    @Test
    fun uprightToBuffer_rot90_center() {
        val bufW = 1920
        val bufH = 1080
        val (dispW, dispH) = PreviewToBufferRoiMapper.displayOrientedBufferSize(bufW, bufH, 90)
        val rx = dispW / 2f
        val ry = dispH / 2f
        val (bx, by) = PreviewToBufferRoiMapper.uprightDisplayPixelToBuffer(rx, ry, bufW, bufH, 90)
        assertTrue(bx in 400f..1520f)
        assertTrue(by in 200f..880f)
    }

    @Test
    fun viewNormalized_corner_topLeft_mapsInsideBuffer_rot90() {
        val bufW = 1920
        val bufH = 1080
        val rot = 90
        val (dispW, dispH) = PreviewToBufferRoiMapper.displayOrientedBufferSize(bufW, bufH, rot)
        val (bx, by) = PreviewToBufferRoiMapper.viewNormalizedToBufferPixel(
            nx = 0f,
            ny = 0f,
            viewWidthPx = 1080,
            viewHeightPx = 2400,
            dispW = dispW,
            dispH = dispH,
            bufW = bufW,
            bufH = bufH,
            rotationDegrees = rot,
        )
        assertTrue(bx >= 0f && bx < bufW)
        assertTrue(by >= 0f && by < bufH)
    }
}
