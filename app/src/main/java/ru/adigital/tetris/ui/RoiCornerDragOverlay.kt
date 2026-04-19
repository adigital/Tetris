package ru.adigital.tetris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ru.adigital.tetris.settings.DualRoiSettings
import ru.adigital.tetris.vision.NormalizedRoi

private val PlayfieldColor = Color(0xFF4CAF50)
private val NextRoiColor = Color(0xFFFF9100)

private enum class WhichRoi { Playfield, Next }

private enum class Corner {
    TopLeft,
    TopRight,
    BottomRight,
    BottomLeft,
}

private data class HitTarget(val which: WhichRoi, val corner: Corner)

private data class DragSession(
    val target: HitTarget,
    val startRoi: NormalizedRoi,
)

/**
 * Две нормализованные зоны на превью: рамки + ручки в углах, перетаскивание за угол меняет ROI.
 */
@Composable
fun RoiCornerDragOverlay(
    dualRoi: DualRoiSettings,
    onDualRoiChange: (DualRoiSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val slopPx = remember(density) { with(density) { 32.dp.toPx() } }
    val handlePx = remember(density) { with(density) { 11.dp.toPx() } }
    val dualRoiUpdated = rememberUpdatedState(dualRoi)
    val onDualRoiUpdated = rememberUpdatedState(onDualRoiChange)

    var dragSession by remember { mutableStateOf<DragSession?>(null) }
    var totalDragPx by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRoiWithHandles(
                roi = dualRoi.playfield,
                strokeColor = PlayfieldColor,
                fillAlpha = 0.10f,
                handlePx = handlePx,
                widthPx = w,
                heightPx = h,
            )
            drawRoiWithHandles(
                roi = dualRoi.nextPiece,
                strokeColor = NextRoiColor,
                fillAlpha = 0.12f,
                handlePx = handlePx,
                widthPx = w,
                heightPx = h,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(slopPx) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            if (w <= 0f || h <= 0f) return@detectDragGestures
                            val hit = hitTestNearestCorner(
                                dualRoi = dualRoiUpdated.value,
                                startOffset = startOffset,
                                boxW = w,
                                boxH = h,
                                slopPx = slopPx,
                            ) ?: return@detectDragGestures
                            val startRoi = when (hit.which) {
                                WhichRoi.Playfield -> dualRoiUpdated.value.playfield
                                WhichRoi.Next -> dualRoiUpdated.value.nextPiece
                            }
                            dragSession = DragSession(hit, startRoi)
                            totalDragPx = Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val session = dragSession ?: return@detectDragGestures
                            totalDragPx += dragAmount
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val dxn = totalDragPx.x / w
                            val dyn = totalDragPx.y / h
                            val s = session.startRoi
                            val newRoi = roiAfterCornerDrag(s, session.target.corner, dxn, dyn)
                            val updated = when (session.target.which) {
                                WhichRoi.Playfield -> dualRoiUpdated.value.copy(playfield = newRoi)
                                WhichRoi.Next -> dualRoiUpdated.value.copy(nextPiece = newRoi)
                            }
                            onDualRoiUpdated.value(updated)
                        },
                        onDragEnd = {
                            dragSession = null
                            totalDragPx = Offset.Zero
                        },
                        onDragCancel = {
                            dragSession = null
                            totalDragPx = Offset.Zero
                        },
                    )
                },
        )
    }
}

private fun roiAfterCornerDrag(
    start: NormalizedRoi,
    corner: Corner,
    dxn: Float,
    dyn: Float,
): NormalizedRoi = when (corner) {
    Corner.TopLeft -> NormalizedRoi.fromRaw(
        start.left + dxn,
        start.top + dyn,
        start.right,
        start.bottom,
    )
    Corner.TopRight -> NormalizedRoi.fromRaw(
        start.left,
        start.top + dyn,
        start.right + dxn,
        start.bottom,
    )
    Corner.BottomRight -> NormalizedRoi.fromRaw(
        start.left,
        start.top,
        start.right + dxn,
        start.bottom + dyn,
    )
    Corner.BottomLeft -> NormalizedRoi.fromRaw(
        start.left + dxn,
        start.top,
        start.right,
        start.bottom + dyn,
    )
}

private fun hitTestNearestCorner(
    dualRoi: DualRoiSettings,
    startOffset: Offset,
    boxW: Float,
    boxH: Float,
    slopPx: Float,
): HitTarget? {
    val slop2 = slopPx * slopPx
    var best: Pair<HitTarget, Float>? = null
    fun consider(which: WhichRoi, roi: NormalizedRoi) {
        for (corner in Corner.entries) {
            val c = cornerPx(roi, corner, boxW, boxH)
            val d2 = (startOffset - c).getDistanceSquared()
            val prev = best?.second
            if (d2 <= slop2 && (prev == null || d2 < prev)) {
                best = HitTarget(which, corner) to d2
            }
        }
    }
    consider(WhichRoi.Next, dualRoi.nextPiece)
    consider(WhichRoi.Playfield, dualRoi.playfield)
    return best?.first
}

private fun cornerPx(roi: NormalizedRoi, corner: Corner, boxW: Float, boxH: Float): Offset {
    val l = roi.left * boxW
    val t = roi.top * boxH
    val r = roi.right * boxW
    val b = roi.bottom * boxH
    return when (corner) {
        Corner.TopLeft -> Offset(l, t)
        Corner.TopRight -> Offset(r, t)
        Corner.BottomRight -> Offset(r, b)
        Corner.BottomLeft -> Offset(l, b)
    }
}

private fun DrawScope.drawRoiWithHandles(
    roi: NormalizedRoi,
    strokeColor: Color,
    fillAlpha: Float,
    handlePx: Float,
    widthPx: Float,
    heightPx: Float,
) {
    val l = roi.left * widthPx
    val t = roi.top * heightPx
    val rw = (roi.right - roi.left) * widthPx
    val rh = (roi.bottom - roi.top) * heightPx
    drawRect(
        color = strokeColor.copy(alpha = fillAlpha),
        topLeft = Offset(l, t),
        size = Size(rw, rh),
    )
    drawRect(
        color = strokeColor,
        topLeft = Offset(l, t),
        size = Size(rw, rh),
        style = Stroke(width = 3.dp.toPx()),
    )
    val hs = handlePx
    val half = hs / 2f
    for (corner in Corner.entries) {
        val c = cornerPx(roi, corner, widthPx, heightPx)
        val tl = Offset(c.x - half, c.y - half)
        val rad = CornerRadius(half * 0.35f, half * 0.35f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.95f),
            topLeft = tl,
            size = Size(hs, hs),
            cornerRadius = rad,
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = tl,
            size = Size(hs, hs),
            cornerRadius = rad,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
