package ru.adigital.tetris.ui

import android.Manifest
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.adigital.tetris.R
import ru.adigital.tetris.camera.hasCameraPermission
import ru.adigital.tetris.settings.loadRoiSettings
import ru.adigital.tetris.settings.saveRoiSettings
import ru.adigital.tetris.ui.theme.TetrisTheme
import ru.adigital.tetris.vision.NormalizedRoi
import ru.adigital.tetris.vision.PlayfieldSnapshot
import ru.adigital.tetris.vision.StubPlayfieldAnalyzer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

@Composable
fun CameraCaptureScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val hasPermission = context.hasCameraPermission()
    var permissionDeniedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) permissionDeniedOnce = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hasPermission) {
            Text(
                text = stringResource(R.string.camera_permission_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    if (activity != null) {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = activity != null,
            ) {
                Text(stringResource(R.string.camera_grant_permission))
            }
            if (permissionDeniedOnce) {
                Text(
                    text = stringResource(R.string.camera_permission_denied),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            CameraPreviewWithRoi(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CameraPreviewWithRoi(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var roi by remember { mutableStateOf(NormalizedRoi.Default) }
    var roiSettingsLoaded by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<PlayfieldSnapshot?>(null) }
    var fps by remember { mutableStateOf("-- fps") }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) { context.loadRoiSettings() }
        if (loaded != null) {
            roi = loaded
        }
        roiSettingsLoaded = true
    }

    LaunchedEffect(roi.left, roi.top, roi.right, roi.bottom, roiSettingsLoaded) {
        if (!roiSettingsLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            context.saveRoiSettings(roi)
        }
    }

    CameraPreviewWithRoiLayout(
        roi = roi,
        onRoiChange = { roi = it },
        fps = fps,
        snapshot = snapshot,
        modifier = modifier,
        preview = {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                roi = roi,
                onSnapshot = { snapshot = it },
                onFps = { fps = it },
            )
        },
    )
}

@Composable
private fun CameraPreviewWithRoiLayout(
    roi: NormalizedRoi,
    onRoiChange: (NormalizedRoi) -> Unit,
    fps: String,
    snapshot: PlayfieldSnapshot?,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { clip = false },
        ) {
            Column(
                modifier = Modifier
                    .width(RoiSideColumnWidth)
                    .fillMaxHeight()
                    .zIndex(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .zIndex(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CompactRoiSliderVertical(
                        value = roi.left,
                        onValueChange = { v ->
                            onRoiChange(NormalizedRoi.fromRaw(v, roi.top, roi.right, roi.bottom))
                        },
                        valueRange = 0f..0.45f,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 4.dp)
                    .zIndex(0f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CompactRoiSlider(
                    value = roi.top,
                    onValueChange = { v ->
                        onRoiChange(NormalizedRoi.fromRaw(roi.left, v, roi.right, roi.bottom))
                    },
                    valueRange = 0f..0.45f,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RectangleShape),
                ) {
                    preview()
                    RoiOverlay(roi = roi, modifier = Modifier.fillMaxSize())
                }
                CompactRoiSlider(
                    value = roi.bottom,
                    onValueChange = { v ->
                        onRoiChange(NormalizedRoi.fromRaw(roi.left, roi.top, roi.right, v))
                    },
                    valueRange = 0.55f..1f,
                )
            }
            Column(
                modifier = Modifier
                    .width(RoiSideColumnWidth)
                    .fillMaxHeight()
                    .zIndex(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .zIndex(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CompactRoiSliderVertical(
                        value = roi.right,
                        onValueChange = { v ->
                            onRoiChange(NormalizedRoi.fromRaw(roi.left, roi.top, v, roi.bottom))
                        },
                        valueRange = 0.55f..1f,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.camera_analysis_stub, fps),
                style = MaterialTheme.typography.bodySmall,
            )
            snapshot?.let { snap ->
                Text(
                    text = stringResource(
                        R.string.camera_snapshot_summary,
                        snap.columns,
                        snap.rows,
                        snap.confidence,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private val RoiSideColumnWidth = 56.dp

private val RoiSliderVisualHeight = 22.dp
private val RoiSliderTrackHeight = 3.dp
private val RoiSliderThumbSize = DpSize(12.dp, 12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactRoiSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors()
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier
            .fillMaxWidth()
            .height(RoiSliderVisualHeight),
        interactionSource = interactionSource,
        colors = colors,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                colors = colors,
                enabled = true,
                thumbSize = RoiSliderThumbSize,
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                enabled = true,
                modifier = Modifier.height(RoiSliderTrackHeight),
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactRoiSliderVertical(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors()
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val maxH = constraints.maxHeight
                    val padPx = with(density) { 16.dp.roundToPx() }
                    val minTrackPx = with(density) { 160.dp.roundToPx() }
                    val trackPx = (maxH - padPx).coerceAtLeast(minTrackPx)
                    val thickPx = with(density) { RoiSliderVisualHeight.roundToPx() }.coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(trackPx, thickPx))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            x = (constraints.maxWidth - placeable.width) / 2,
                            y = (constraints.maxHeight - placeable.height) / 2,
                        )
                    }
                }
                .graphicsLayer {
                    rotationZ = -90f
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    clip = false
                },
            interactionSource = interactionSource,
            colors = colors,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    enabled = true,
                    thumbSize = RoiSliderThumbSize,
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = colors,
                    enabled = true,
                    modifier = Modifier.height(RoiSliderTrackHeight),
                )
            },
        )
    }
}

@Composable
private fun RoiOverlay(roi: NormalizedRoi, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val left = roi.left * w
        val top = roi.top * h
        val rw = (roi.right - roi.left) * w
        val rh = (roi.bottom - roi.top) * h
        drawRect(
            color = Color.Green.copy(alpha = 0.12f),
            topLeft = Offset(left, top),
            size = Size(rw, rh),
        )
        drawRect(
            color = Color.Green,
            topLeft = Offset(left, top),
            size = Size(rw, rh),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun CameraPreview(
    roi: NormalizedRoi,
    onSnapshot: (PlayfieldSnapshot) -> Unit,
    onFps: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val roiState = rememberUpdatedState(roi)
    val onSnapshotState = rememberUpdatedState(onSnapshot)
    val onFpsState = rememberUpdatedState(onFps)
    val providerRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val fpsAcc = remember {
        object {
            var frames: Int = 0
            var windowStartNs: Long = 0L
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                providerRef.getAndSet(null)?.unbindAll()
            } catch (_: Exception) {
            }
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                post {
                    val appContext = ctx.applicationContext
                    val mainExecutor = ContextCompat.getMainExecutor(appContext)
                    val future = ProcessCameraProvider.getInstance(appContext)
                    future.addListener(
                        {
                            val cameraProvider = future.get()
                            providerRef.set(cameraProvider)
                            val preview = CameraXPreview.Builder().build().also { p ->
                                p.surfaceProvider = surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { image ->
                                val snap = StubPlayfieldAnalyzer.analyze(image, roiState.value)
                                fpsAcc.frames++
                                val now = System.nanoTime()
                                if (fpsAcc.windowStartNs == 0L) {
                                    fpsAcc.windowStartNs = now
                                }
                                if (now - fpsAcc.windowStartNs >= 1_000_000_000L) {
                                    val v = fpsAcc.frames
                                    fpsAcc.frames = 0
                                    fpsAcc.windowStartNs = now
                                    mainExecutor.execute {
                                        onFpsState.value("$v fps")
                                    }
                                }
                                mainExecutor.execute {
                                    onSnapshotState.value(snap)
                                }
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                            } catch (_: Exception) {
                            }
                        },
                        mainExecutor,
                    )
                }
            }
        },
    )
}

@Preview(name = "Камера: ROI и слайдеры", showBackground = true, heightDp = 780, widthDp = 360)
@Composable
private fun CameraPreviewWithRoiLayoutPreview() {
    TetrisTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var roi by remember { mutableStateOf(NormalizedRoi.Default) }
            CameraPreviewWithRoiLayout(
                roi = roi,
                onRoiChange = { roi = it },
                fps = "30 fps",
                snapshot = PlayfieldSnapshot.unknownGrid(),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                preview = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Text(
                            text = "Заглушка превью",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Preview(name = "Экран камеры (запрос разрешения)", showBackground = true, heightDp = 640, widthDp = 360)
@Composable
private fun CameraCaptureScreenPreview() {
    TetrisTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CameraCaptureScreen(modifier = Modifier.fillMaxSize())
        }
    }
}
