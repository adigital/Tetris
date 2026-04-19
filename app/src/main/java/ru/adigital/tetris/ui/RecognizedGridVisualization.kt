package ru.adigital.tetris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.adigital.tetris.R
import ru.adigital.tetris.settings.CvOtsuThresholdBiasRange
import ru.adigital.tetris.settings.DualRoiSettings
import ru.adigital.tetris.vision.CellKind
import ru.adigital.tetris.vision.NormalizedRoi
import ru.adigital.tetris.vision.PlayfieldSnapshot

private const val PlayfieldCols = 10
private const val PlayfieldRows = 20
private const val NextCols = 4
private const val NextRows = 4

/**
 * Полупрозрачная подсветка **только** клеток [CellKind.Filled] внутри нормализованных ROI поверх превью камеры.
 * Пустые и неизвестные клетки не рисуются — фон остаётся изображением с камеры.
 */
@Composable
fun RoiAlignedRecognitionOverlay(
    dualRoi: DualRoiSettings,
    playfield: PlayfieldSnapshot?,
    nextPiece: PlayfieldSnapshot?,
    modifier: Modifier = Modifier,
) {
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    Canvas(modifier = modifier) {
        drawSnapshotInNormalizedRoi(
            roi = dualRoi.playfield,
            snapshot = playfield,
            expectedCols = PlayfieldCols,
            expectedRows = PlayfieldRows,
            filledColor = highlight,
        )
        drawSnapshotInNormalizedRoi(
            roi = dualRoi.nextPiece,
            snapshot = nextPiece,
            expectedCols = NextCols,
            expectedRows = NextRows,
            filledColor = highlight,
        )
    }
}

private fun DrawScope.drawSnapshotInNormalizedRoi(
    roi: NormalizedRoi,
    snapshot: PlayfieldSnapshot?,
    expectedCols: Int,
    expectedRows: Int,
    filledColor: Color,
) {
    val snap = snapshot ?: return
    if (snap.columns != expectedCols || snap.rows != expectedRows) return
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val l = roi.left * w
    val t = roi.top * h
    val rw = (roi.right - roi.left) * w
    val rh = (roi.bottom - roi.top) * h
    if (rw <= 1f || rh <= 1f) return
    val cw = rw / expectedCols
    val ch = rh / expectedRows
    val pad = (1.dp.toPx()).coerceAtMost(minOf(cw, ch) * 0.12f)
    for (i in snap.cells.indices) {
        if (snap.cells[i] != CellKind.Filled) continue
        val cx = i % expectedCols
        val ry = i / expectedCols
        drawRect(
            color = filledColor,
            topLeft = Offset(l + cx * cw + pad, t + ry * ch + pad),
            size = Size(
                (cw - 2 * pad).coerceAtLeast(1f),
                (ch - 2 * pad).coerceAtLeast(1f),
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CvOtsuThresholdBiasControls(
    otsuThresholdBias: Float,
    onOtsuThresholdBiasChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.camera_cv_otsu_bias_title),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
        )
        Text(
            text = stringResource(R.string.camera_cv_otsu_bias_value, otsuThresholdBias),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value = otsuThresholdBias,
            onValueChange = onOtsuThresholdBiasChange,
            valueRange = -CvOtsuThresholdBiasRange..CvOtsuThresholdBiasRange,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(),
        )
        Text(
            text = stringResource(R.string.camera_cv_otsu_bias_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
        )
    }
}
