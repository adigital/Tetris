package ru.adigital.tetris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.adigital.tetris.R
import ru.adigital.tetris.settings.CvOtsuThresholdBiasRange
import ru.adigital.tetris.vision.CellKind
import ru.adigital.tetris.vision.PlayfieldSnapshot

private const val PlayfieldCols = 10
private const val PlayfieldRows = 20
private const val NextCols = 4
private const val NextRows = 4

/**
 * Слева: стакан 10×20 с **квадратными** ячейками (ширина : высота = 1 : 2).
 * Справа: колонка той же высоты, что и сетка стакана — мини 4×4 с тем же размером ячейки, под ним слайдер порога.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognizedCvRecognitionPanel(
    playfield: PlayfieldSnapshot?,
    nextPiece: PlayfieldSnapshot?,
    otsuThresholdBias: Float,
    onOtsuThresholdBiasChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = 8.dp
    val titleRowH = 22.dp
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val bodyH = maxHeight - titleRowH
        val sDp = minOf(
            bodyH / PlayfieldRows.toFloat(),
            (maxWidth - gap) / (PlayfieldCols + NextCols).toFloat(),
        ).coerceAtLeast(0.5.dp)
        val playW = sDp * PlayfieldCols
        val playH = sDp * PlayfieldRows
        val nextSize = sDp * NextCols
        val rightW = (maxWidth - playW - gap).coerceAtLeast(nextSize)

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(titleRowH),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                Text(
                    text = stringResource(R.string.camera_label_playfield),
                    modifier = Modifier.width(playW),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.camera_label_next),
                    modifier = Modifier.width(rightW),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(playH),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                Box(
                    modifier = Modifier
                        .size(playW, playH)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (playfield != null &&
                        playfield.columns == PlayfieldCols &&
                        playfield.rows == PlayfieldRows
                    ) {
                        SnapshotGrid(
                            snapshot = playfield,
                            modifier = Modifier.fillMaxWidth()
                                .height(playH),
                        )
                    } else {
                        SnapshotGridPlaceholder(
                            columns = PlayfieldCols,
                            rows = PlayfieldRows,
                            modifier = Modifier.fillMaxWidth().height(playH),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .width(rightW)
                        .height(playH),
                ) {
                    Box(
                        modifier = Modifier
                            .size(nextSize)
                            .align(Alignment.CenterHorizontally)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        if (nextPiece != null &&
                            nextPiece.columns == NextCols &&
                            nextPiece.rows == NextRows
                        ) {
                            SnapshotGrid(
                                snapshot = nextPiece,
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            )
                        } else {
                            SnapshotGridPlaceholder(
                                columns = NextCols,
                                rows = NextRows,
                                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
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
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotGrid(
    snapshot: PlayfieldSnapshot,
    modifier: Modifier = Modifier,
) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val unknown = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val gridLine = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Canvas(modifier = modifier) {
        val cols = snapshot.columns
        val rows = snapshot.rows
        val cw = size.width / cols
        val ch = size.height / rows
        val pad = 0.5f
        for (i in snapshot.cells.indices) {
            val cx = i % cols
            val ry = i / cols
            val color = when (snapshot.cells[i]) {
                CellKind.Filled -> filled
                CellKind.Empty -> empty
                CellKind.Unknown -> unknown
            }
            drawRect(
                color = color,
                topLeft = Offset(cx * cw + pad, ry * ch + pad),
                size = Size((cw - 2 * pad).coerceAtLeast(1f), (ch - 2 * pad).coerceAtLeast(1f)),
            )
        }
        for (c in 1 until cols) {
            val x = c * cw
            drawLine(gridLine, Offset(x, 0f), Offset(x, size.height), strokeWidth = 0.5f)
        }
        for (r in 1 until rows) {
            val y = r * ch
            drawLine(gridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5f)
        }
    }
}

@Composable
private fun SnapshotGridPlaceholder(
    columns: Int,
    rows: Int,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Canvas(modifier = modifier) {
        val cw = size.width / columns
        val ch = size.height / rows
        for (c in 0..columns) {
            val x = c * cw
            drawLine(outline, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        }
        for (r in 0..rows) {
            val y = r * ch
            drawLine(outline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
    }
}
