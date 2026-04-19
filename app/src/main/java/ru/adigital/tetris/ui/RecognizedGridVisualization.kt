package ru.adigital.tetris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.adigital.tetris.R
import ru.adigital.tetris.vision.CellKind
import ru.adigital.tetris.vision.PlayfieldSnapshot

@Composable
fun RecognizedDualGridsRow(
    playfield: PlayfieldSnapshot?,
    nextPiece: PlayfieldSnapshot?,
    modifier: Modifier = Modifier,
    rowHeightDp: Int = 188,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeightDp.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecognizedGridColumn(
            title = stringResource(R.string.camera_label_playfield),
            snapshot = playfield,
            expectedColumns = 10,
            expectedRows = 20,
            modifier = Modifier
                .weight(0.62f)
                .fillMaxHeight(),
        )
        RecognizedGridColumn(
            title = stringResource(R.string.camera_label_next),
            snapshot = nextPiece,
            expectedColumns = 4,
            expectedRows = 4,
            modifier = Modifier
                .weight(0.38f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun RecognizedGridColumn(
    title: String,
    snapshot: PlayfieldSnapshot?,
    expectedColumns: Int,
    expectedRows: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (snapshot != null && snapshot.columns == expectedColumns && snapshot.rows == expectedRows) {
                SnapshotGrid(
                    snapshot = snapshot,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SnapshotGridPlaceholder(
                    columns = expectedColumns,
                    rows = expectedRows,
                    modifier = Modifier.fillMaxSize(),
                )
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
