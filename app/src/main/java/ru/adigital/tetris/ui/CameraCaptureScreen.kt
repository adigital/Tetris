package ru.adigital.tetris.ui

import android.Manifest
import android.content.Context
import android.os.Build
import android.view.Surface
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.adigital.tetris.R
import ru.adigital.tetris.camera.hasCameraPermission
import ru.adigital.tetris.settings.DualRoiSettings
import ru.adigital.tetris.settings.loadDualRoiSettings
import ru.adigital.tetris.settings.saveDualRoiSettings
import ru.adigital.tetris.ui.theme.TetrisTheme
import ru.adigital.tetris.vision.DualPlayfieldAnalyzer
import ru.adigital.tetris.vision.DualPlayfieldSnapshots
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
    var dualRoi by remember { mutableStateOf(DualRoiSettings.Default) }
    var roiSettingsLoaded by remember { mutableStateOf(false) }
    var dualSnapshots by remember { mutableStateOf<DualPlayfieldSnapshots?>(null) }
    var fps by remember { mutableStateOf("-- fps") }
    var analysisBufferWidth by remember { mutableStateOf<Int?>(null) }
    var analysisBufferHeight by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        dualRoi = withContext(Dispatchers.IO) { context.loadDualRoiSettings() }
        roiSettingsLoaded = true
    }

    LaunchedEffect(
        dualRoi.playfield.left,
        dualRoi.playfield.top,
        dualRoi.playfield.right,
        dualRoi.playfield.bottom,
        dualRoi.nextPiece.left,
        dualRoi.nextPiece.top,
        dualRoi.nextPiece.right,
        dualRoi.nextPiece.bottom,
        roiSettingsLoaded,
    ) {
        if (!roiSettingsLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            context.saveDualRoiSettings(dualRoi)
        }
    }

    CameraPreviewWithRoiLayout(
        dualRoi = dualRoi,
        onDualRoiChange = { dualRoi = it },
        fps = fps,
        dualSnapshots = dualSnapshots,
        analysisBufferWidth = analysisBufferWidth,
        analysisBufferHeight = analysisBufferHeight,
        modifier = modifier,
        preview = {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                dualRoi = dualRoi,
                onDualSnapshots = { dualSnapshots = it },
                onFps = { fps = it },
                onAnalysisBufferSize = { w, h ->
                    analysisBufferWidth = w
                    analysisBufferHeight = h
                },
            )
        },
    )
}

@Composable
private fun CameraPreviewWithRoiLayout(
    dualRoi: DualRoiSettings,
    onDualRoiChange: (DualRoiSettings) -> Unit,
    fps: String,
    dualSnapshots: DualPlayfieldSnapshots?,
    analysisBufferWidth: Int?,
    analysisBufferHeight: Int?,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.camera_roi_corner_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { clip = false },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
                val ratio = if (analysisBufferWidth != null && analysisBufferHeight != null &&
                    analysisBufferHeight > 0
                ) {
                    analysisBufferWidth.toFloat() / analysisBufferHeight.toFloat()
                } else {
                    null
                }
                val previewBoxModifier = if (ratio != null && ratio > 0f) {
                    val cw = maxWidth.value
                    val ch = maxHeight.value
                    if (ch > 0f && cw / ch > ratio) {
                        Modifier.fillMaxHeight().aspectRatio(ratio)
                    } else {
                        Modifier.fillMaxWidth().aspectRatio(ratio)
                    }
                } else {
                    Modifier.fillMaxSize()
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = previewBoxModifier
                            .clip(RectangleShape),
                    ) {
                        preview()
                        RoiCornerDragOverlay(
                            dualRoi = dualRoi,
                            onDualRoiChange = onDualRoiChange,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        RecognizedDualGridsRow(
            playfield = dualSnapshots?.playfield,
            nextPiece = dualSnapshots?.nextPiece,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.camera_analysis_stub, fps),
                style = MaterialTheme.typography.bodySmall,
            )
            dualSnapshots?.let { d ->
                Text(
                    text = stringResource(
                        R.string.camera_snapshot_summary,
                        d.playfield.columns,
                        d.playfield.rows,
                        d.playfield.confidence,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = stringResource(
                        R.string.camera_next_summary,
                        d.nextPiece.columns,
                        d.nextPiece.rows,
                        d.nextPiece.confidence,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/**
 * Размеры кадра анализа в координатах, совпадающих с тем, как буфер показывается в портретном UI
 * (учёт [androidx.camera.core.ImageInfo.rotationDegrees]).
 */
private fun displayOrientedAnalysisSize(
    bufferWidth: Int,
    bufferHeight: Int,
    rotationDegrees: Int,
): Pair<Int, Int> =
    if (rotationDegrees == 90 || rotationDegrees == 270) {
        bufferHeight to bufferWidth
    } else {
        bufferWidth to bufferHeight
    }

@Suppress("DEPRECATION")
private fun Context.displayRotationCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: Surface.ROTATION_0
    } else {
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay
            .rotation
    }

@Composable
private fun CameraPreview(
    dualRoi: DualRoiSettings,
    onDualSnapshots: (DualPlayfieldSnapshots) -> Unit,
    onFps: (String) -> Unit,
    onAnalysisBufferSize: ((Int, Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val dualRoiState = rememberUpdatedState(dualRoi)
    val onDualSnapshotsState = rememberUpdatedState(onDualSnapshots)
    val onFpsState = rememberUpdatedState(onFps)
    val providerRef = remember { AtomicReference<ProcessCameraProvider?>(null) }
    val imageAnalysisRef = remember { AtomicReference<ImageAnalysis?>(null) }
    /** После dispose экрана камеры — не вызывать bind (избегаем утечки и «max cameras»). */
    val cameraReleased = remember { AtomicBoolean(false) }
    val fpsAcc = remember {
        object {
            var frames: Int = 0
            var windowStartNs: Long = 0L
        }
    }
    /** Упакованные width<<32|height; -1 = ещё не было кадра. */
    val lastAnalysisPacked = remember { AtomicLong(-1L) }
    val onAnalysisBufferSizeState = rememberUpdatedState(onAnalysisBufferSize)

    DisposableEffect(lifecycleOwner) {
        cameraReleased.set(false)
        onDispose {
            cameraReleased.set(true)
            lastAnalysisPacked.set(-1L)
            imageAnalysisRef.getAndSet(null)?.clearAnalyzer()
            try {
                providerRef.getAndSet(null)?.unbindAll()
            } catch (_: Exception) {
            }
            executor.shutdownNow()
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
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                post {
                    val appContext = ctx.applicationContext
                    val mainExecutor = ContextCompat.getMainExecutor(appContext)
                    val future = ProcessCameraProvider.getInstance(appContext)
                    future.addListener(
                        {
                            if (cameraReleased.get()) return@addListener
                            val cameraProvider = try {
                                future.get()
                            } catch (_: Exception) {
                                return@addListener
                            }
                            if (cameraReleased.get()) return@addListener
                            providerRef.set(cameraProvider)
                            val rotation = ctx.displayRotationCompat()
                            val preview = CameraXPreview.Builder()
                                .setTargetRotation(rotation)
                                .build()
                                .also { p ->
                                    p.surfaceProvider = surfaceProvider
                                }
                            val analysis = ImageAnalysis.Builder()
                                .setTargetRotation(rotation)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            analysis.setAnalyzer(executor) { image ->
                                val w = image.width
                                val h = image.height
                                val rot = image.imageInfo.rotationDegrees
                                val (dw, dh) = displayOrientedAnalysisSize(w, h, rot)
                                if (dw > 0 && dh > 0) {
                                    val packed = (dw.toLong() shl 32) or (dh.toLong() and 0xffff_ffffL)
                                    if (lastAnalysisPacked.get() != packed) {
                                        lastAnalysisPacked.set(packed)
                                        val cb = onAnalysisBufferSizeState.value
                                        if (cb != null) {
                                            mainExecutor.execute { cb(dw, dh) }
                                        }
                                    }
                                }
                                val dual = DualPlayfieldAnalyzer.analyze(
                                    image,
                                    dualRoiState.value.playfield,
                                    dualRoiState.value.nextPiece,
                                )
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
                                    onDualSnapshotsState.value(dual)
                                }
                            }
                            if (cameraReleased.get()) {
                                analysis.clearAnalyzer()
                                return@addListener
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    analysis,
                                )
                                if (!cameraReleased.get()) {
                                    imageAnalysisRef.set(analysis)
                                } else {
                                    analysis.clearAnalyzer()
                                    cameraProvider.unbindAll()
                                }
                            } catch (_: Exception) {
                                analysis.clearAnalyzer()
                            }
                        },
                        mainExecutor,
                    )
                }
            }
        },
    )
}

@Preview(name = "Камера: ROI углами", showBackground = true, heightDp = 780, widthDp = 360)
@Composable
private fun CameraPreviewWithRoiLayoutPreview() {
    TetrisTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            var dual by remember { mutableStateOf(DualRoiSettings.Default) }
            CameraPreviewWithRoiLayout(
                dualRoi = dual,
                onDualRoiChange = { dual = it },
                fps = "30 fps",
                dualSnapshots = null,
                analysisBufferWidth = null,
                analysisBufferHeight = null,
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
