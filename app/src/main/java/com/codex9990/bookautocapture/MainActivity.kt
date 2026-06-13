package com.codex9990.bookautocapture

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.codex9990.bookautocapture.camera.LumaFrameAnalyzer
import com.codex9990.bookautocapture.capture.AutoCaptureStateMachine
import com.codex9990.bookautocapture.capture.BlockReason
import com.codex9990.bookautocapture.capture.CaptureDecision
import com.codex9990.bookautocapture.capture.CaptureSettings
import com.codex9990.bookautocapture.capture.CaptureState
import com.codex9990.bookautocapture.capture.FrameMetrics
import com.codex9990.bookautocapture.capture.Sensitivity
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var frameAnalyzer: LumaFrameAnalyzer

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val stateMachine = AutoCaptureStateMachine()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var uiState by mutableStateOf(CaptureUiState())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        uiState = uiState.copy(
            hasCameraPermission = granted,
            statusText = if (granted) "待機中" else "カメラ権限が必要です",
            errorMessage = if (granted) null else "カメラ権限を許可するとプレビューを表示できます"
        )
        if (granted) {
            bindCameraUseCases()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cleanupTemporaryFiles()

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        frameAnalyzer = LumaFrameAnalyzer { metrics ->
            runOnUiThread { handleFrameMetrics(metrics) }
        }
        configureTapToFocus()

        uiState = loadInitialState()
        stateMachine.settings = uiState.toCaptureSettings()

        setContent {
            BookAutoCaptureTheme {
                BookAutoCaptureScreen(
                    uiState = uiState,
                    previewView = previewView,
                    onRequestPermission = ::requestCameraPermission,
                    onStart = ::startAutoCapture,
                    onStop = ::stopAutoCapture,
                    onManualCapture = ::captureSinglePage,
                    onDeleteLastCapture = ::deleteLastCapture,
                    onSoundEnabledChange = { updateSettings(soundEnabled = it) },
                    onStableDurationChange = { updateSettings(stableDurationMs = it) },
                    onMinIntervalChange = { updateSettings(minCaptureIntervalMs = it) },
                    onSensitivityChange = { updateSettings(sensitivity = it) },
                    onBlurCheckChange = { updateSettings(blurCheckEnabled = it) },
                    onDarknessCheckChange = { updateSettings(darknessCheckEnabled = it) }
                )
            }
        }

        if (uiState.hasCameraPermission) {
            bindCameraUseCases()
        } else {
            requestCameraPermission()
        }
    }

    override fun onStop() {
        super.onStop()
        if (uiState.isRunning) {
            stopAutoCapture()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTargetRotation()
    }

    override fun onDestroy() {
        setScreenAwake(false)
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun loadInitialState(): CaptureUiState {
        val preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sensitivityName = preferences.getString(KEY_SENSITIVITY, Sensitivity.MEDIUM.name)
        val sensitivity = runCatching {
            Sensitivity.valueOf(sensitivityName ?: Sensitivity.MEDIUM.name)
        }.getOrDefault(Sensitivity.MEDIUM)

        return CaptureUiState(
            hasCameraPermission = hasCameraPermission(),
            soundEnabled = preferences.getBoolean(KEY_SOUND_ENABLED, true),
            stableDurationMs = preferences.getLong(KEY_STABLE_DURATION_MS, 1_000L),
            minCaptureIntervalMs = preferences.getLong(KEY_MIN_CAPTURE_INTERVAL_MS, 2_000L),
            sensitivity = sensitivity,
            blurCheckEnabled = preferences.getBoolean(KEY_BLUR_CHECK_ENABLED, true),
            darknessCheckEnabled = preferences.getBoolean(KEY_DARKNESS_CHECK_ENABLED, true),
            statusText = if (hasCameraPermission()) "待機中" else "カメラ権限が必要です"
        )
    }

    private fun updateSettings(
        soundEnabled: Boolean = uiState.soundEnabled,
        stableDurationMs: Long = uiState.stableDurationMs,
        minCaptureIntervalMs: Long = uiState.minCaptureIntervalMs,
        sensitivity: Sensitivity = uiState.sensitivity,
        blurCheckEnabled: Boolean = uiState.blurCheckEnabled,
        darknessCheckEnabled: Boolean = uiState.darknessCheckEnabled
    ) {
        uiState = uiState.copy(
            soundEnabled = soundEnabled,
            stableDurationMs = stableDurationMs.coerceIn(500L, 2_000L),
            minCaptureIntervalMs = minCaptureIntervalMs.coerceIn(1_000L, 5_000L),
            sensitivity = sensitivity,
            blurCheckEnabled = blurCheckEnabled,
            darknessCheckEnabled = darknessCheckEnabled
        )
        stateMachine.settings = uiState.toCaptureSettings()

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUND_ENABLED, uiState.soundEnabled)
            .putLong(KEY_STABLE_DURATION_MS, uiState.stableDurationMs)
            .putLong(KEY_MIN_CAPTURE_INTERVAL_MS, uiState.minCaptureIntervalMs)
            .putString(KEY_SENSITIVITY, uiState.sensitivity.name)
            .putBoolean(KEY_BLUR_CHECK_ENABLED, uiState.blurCheckEnabled)
            .putBoolean(KEY_DARKNESS_CHECK_ENABLED, uiState.darknessCheckEnabled)
            .apply()
    }

    private fun requestCameraPermission() {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun bindCameraUseCases() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                try {
                    val provider = providerFuture.get()
                    cameraProvider = provider

                    val rotation = currentRotation()
                    val preview = Preview.Builder()
                        .setTargetRotation(rotation)
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(100)
                        .setTargetRotation(rotation)
                        .build()
                    imageCapture = capture

                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(320, 240),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                                    )
                                )
                                .build()
                        )
                        .setTargetRotation(rotation)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { it.setAnalyzer(cameraExecutor, frameAnalyzer) }

                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        capture,
                        analysis
                    )

                    uiState = uiState.copy(
                        cameraReady = true,
                        errorMessage = null,
                        statusText = if (uiState.isRunning) uiState.statusText else "待機中"
                    )
                } catch (error: Exception) {
                    uiState = uiState.copy(
                        cameraReady = false,
                        statusText = "エラー",
                        errorMessage = "カメラの起動に失敗しました: ${error.message.orEmpty()}"
                    )
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureTapToFocus() {
        previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val meteringPoint = previewView.meteringPointFactory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(
                    meteringPoint,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                )
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                camera?.cameraControl?.startFocusAndMetering(action)
                true
            } else {
                true
            }
        }
    }

    private fun startAutoCapture() {
        if (!uiState.hasCameraPermission) {
            requestCameraPermission()
            return
        }
        if (imageCapture == null) {
            uiState = uiState.copy(statusText = "エラー", errorMessage = "カメラ準備中です")
            return
        }

        val sessionFolder = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .format(LocalDateTime.now())
        frameAnalyzer.reset()
        stateMachine.settings = uiState.toCaptureSettings()
        stateMachine.start(SystemClock.elapsedRealtime())

        uiState = uiState.copy(
            isRunning = true,
            isCapturing = false,
            captureCount = 0,
            sessionFolder = sessionFolder,
            saveFolder = "$BASE_SAVE_FOLDER/$sessionFolder",
            lastFileName = "-",
            capturedPages = emptyList(),
            statusText = "ページめくり待ち",
            errorMessage = null
        )
        setScreenAwake(true)
    }

    private fun stopAutoCapture() {
        stateMachine.stop()
        uiState = uiState.copy(
            isRunning = false,
            isCapturing = false,
            statusText = "待機中"
        )
        setScreenAwake(false)
    }

    private fun captureSinglePage() {
        if (!uiState.hasCameraPermission) {
            requestCameraPermission()
            return
        }
        ensureSession()
        capturePage()
    }

    private fun ensureSession() {
        if (uiState.sessionFolder.isNotBlank()) return

        val sessionFolder = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .format(LocalDateTime.now())
        uiState = uiState.copy(
            sessionFolder = sessionFolder,
            saveFolder = "$BASE_SAVE_FOLDER/$sessionFolder"
        )
    }

    private fun handleFrameMetrics(metrics: FrameMetrics) {
        if (!uiState.isRunning || uiState.isCapturing) return

        stateMachine.settings = uiState.toCaptureSettings()
        val decision = stateMachine.onFrame(metrics)
        val statusText = decision.toStatusText()
        if (statusText != uiState.statusText) {
            uiState = uiState.copy(statusText = statusText, errorMessage = null)
        }

        if (decision.shouldCapture) {
            capturePage()
        }
    }

    private fun capturePage() {
        val capture = imageCapture
        if (capture == null) {
            uiState = uiState.copy(statusText = "エラー", errorMessage = "カメラがまだ準備できていません")
            return
        }
        if (uiState.isCapturing) return

        ensureSession()
        val nextPageNumber = uiState.captureCount + 1
        val fileName = String.format(Locale.US, "page_%04d.jpg", nextPageNumber)
        val outputOptions = createOutputOptions(fileName, uiState.sessionFolder)
        capture.targetRotation = currentRotation()

        uiState = uiState.copy(isCapturing = true, statusText = "撮影中", errorMessage = null)
        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    outputFileResults.savedUri?.let { uri ->
                        contentResolver.update(
                            uri,
                            ContentValues().apply {
                                put(MediaStore.Images.Media.IS_PENDING, 0)
                            },
                            null,
                            null
                        )
                    }
                    runOnUiThread {
                        if (uiState.soundEnabled) {
                            playCaptureSound()
                        }
                        if (uiState.isRunning) {
                            stateMachine.markCaptureCompleted(SystemClock.elapsedRealtime())
                        }
                        uiState = uiState.copy(
                            isCapturing = false,
                            captureCount = nextPageNumber,
                            lastFileName = fileName,
                            capturedPages = uiState.capturedPages + CapturedPage(
                                fileName = fileName,
                                uri = outputFileResults.savedUri
                            ),
                            statusText = "撮影完了",
                            errorMessage = null
                        )
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        if (uiState.isRunning) {
                            stateMachine.markCaptureFailed()
                        }
                        uiState = uiState.copy(
                            isCapturing = false,
                            statusText = "エラー",
                            errorMessage = "保存に失敗しました: ${exception.message.orEmpty()}"
                        )
                    }
                }
            }
        )
    }

    private fun deleteLastCapture() {
        if (uiState.isRunning || uiState.isCapturing) return

        val page = uiState.capturedPages.lastOrNull()
        if (page == null) {
            uiState = uiState.copy(errorMessage = "削除できる撮影画像がありません")
            return
        }

        val uri = page.uri
        if (uri == null) {
            uiState = uiState.copy(errorMessage = "保存先URIが取得できなかったため削除できません")
            return
        }

        val deleted = runCatching {
            contentResolver.delete(uri, null, null)
        }.getOrElse { error ->
            uiState = uiState.copy(errorMessage = "削除に失敗しました: ${error.message.orEmpty()}")
            return
        }

        if (deleted <= 0) {
            uiState = uiState.copy(errorMessage = "削除対象の画像が見つかりませんでした")
            return
        }

        val remainingPages = uiState.capturedPages.dropLast(1)
        uiState = uiState.copy(
            capturedPages = remainingPages,
            captureCount = remainingPages.size,
            lastFileName = remainingPages.lastOrNull()?.fileName ?: "-",
            statusText = "最後の撮影を削除",
            errorMessage = null
        )
    }

    private fun createOutputOptions(
        fileName: String,
        sessionFolder: String
    ): ImageCapture.OutputFileOptions {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "$BASE_SAVE_FOLDER/$sessionFolder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        return ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()
    }

    private fun cleanupTemporaryFiles() {
        val tmpDir = File(cacheDir, "tmp")
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
            return
        }
        tmpDir.listFiles()?.forEach { file ->
            runCatching { file.deleteRecursively() }
        }
    }

    private fun currentRotation(): Int {
        return previewView.display?.rotation ?: Surface.ROTATION_0
    }

    private fun updateTargetRotation() {
        imageCapture?.targetRotation = currentRotation()
    }

    private fun setScreenAwake(keepAwake: Boolean) {
        if (keepAwake) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun playCaptureSound() {
        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 90)
        Handler(Looper.getMainLooper()).postDelayed(
            { toneGenerator.release() },
            200L
        )
    }

    private fun CaptureDecision.toStatusText(): String {
        return when (blockReason) {
            BlockReason.TOO_DARK -> "暗すぎます"
            BlockReason.TOO_BLURRY -> "ブレています"
            BlockReason.COOLDOWN -> "クールダウン中"
            BlockReason.NONE -> when (state) {
                CaptureState.IDLE -> "待機中"
                CaptureState.WAITING_FOR_PAGE_TURN -> "ページめくり待ち"
                CaptureState.PAGE_MOVING -> "ページめくり検出"
                CaptureState.WAITING_FOR_STABLE -> "安定待ち"
                CaptureState.CAPTURING -> "撮影中"
                CaptureState.COOLDOWN -> "クールダウン中"
            }
        }
    }

    private fun CaptureUiState.toCaptureSettings(): CaptureSettings {
        return CaptureSettings(
            stableDurationMs = stableDurationMs,
            minCaptureIntervalMs = minCaptureIntervalMs,
            sensitivity = sensitivity,
            blurCheckEnabled = blurCheckEnabled,
            darknessCheckEnabled = darknessCheckEnabled
        )
    }

    companion object {
        private const val PREFS_NAME = "book_auto_capture_settings"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_STABLE_DURATION_MS = "stable_duration_ms"
        private const val KEY_MIN_CAPTURE_INTERVAL_MS = "min_capture_interval_ms"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_BLUR_CHECK_ENABLED = "blur_check_enabled"
        private const val KEY_DARKNESS_CHECK_ENABLED = "darkness_check_enabled"
        private const val BASE_SAVE_FOLDER = "Pictures/BookAutoCapture"
    }
}

private data class CaptureUiState(
    val hasCameraPermission: Boolean = false,
    val cameraReady: Boolean = false,
    val isRunning: Boolean = false,
    val isCapturing: Boolean = false,
    val captureCount: Int = 0,
    val statusText: String = "待機中",
    val errorMessage: String? = null,
    val soundEnabled: Boolean = true,
    val stableDurationMs: Long = 1_000L,
    val minCaptureIntervalMs: Long = 2_000L,
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val blurCheckEnabled: Boolean = true,
    val darknessCheckEnabled: Boolean = true,
    val sessionFolder: String = "",
    val saveFolder: String = "Pictures/BookAutoCapture",
    val lastFileName: String = "-",
    val capturedPages: List<CapturedPage> = emptyList()
)

private data class CapturedPage(
    val fileName: String,
    val uri: Uri?
)

@Composable
private fun BookAutoCaptureTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF256D46),
            onPrimary = Color.White,
            secondary = Color(0xFF6B5B95),
            tertiary = Color(0xFFB85C38),
            background = Color(0xFFF7F7F2),
            surface = Color.White,
            surfaceVariant = Color(0xFFE9ECE3),
            onSurface = Color(0xFF1B1D1A)
        ),
        content = content
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BookAutoCaptureScreen(
    uiState: CaptureUiState,
    previewView: PreviewView,
    onRequestPermission: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onManualCapture: () -> Unit,
    onDeleteLastCapture: () -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onStableDurationChange: (Long) -> Unit,
    onMinIntervalChange: (Long) -> Unit,
    onSensitivityChange: (Sensitivity) -> Unit,
    onBlurCheckChange: (Boolean) -> Unit,
    onDarknessCheckChange: (Boolean) -> Unit
) {
    var showSettings by androidx.compose.runtime.remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                PreviewPane(
                    uiState = uiState,
                    previewView = previewView,
                    onRequestPermission = onRequestPermission,
                    modifier = Modifier
                        .weight(1.35f)
                        .fillMaxHeight()
                )
                ControlPanel(
                    uiState = uiState,
                    showSettings = showSettings,
                    onToggleSettings = { showSettings = !showSettings },
                    onStart = onStart,
                    onStop = onStop,
                    onManualCapture = onManualCapture,
                    onDeleteLastCapture = onDeleteLastCapture,
                    onSoundEnabledChange = onSoundEnabledChange,
                    onStableDurationChange = onStableDurationChange,
                    onMinIntervalChange = onMinIntervalChange,
                    onSensitivityChange = onSensitivityChange,
                    onBlurCheckChange = onBlurCheckChange,
                    onDarknessCheckChange = onDarknessCheckChange,
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                PreviewPane(
                    uiState = uiState,
                    previewView = previewView,
                    onRequestPermission = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                ControlPanel(
                    uiState = uiState,
                    showSettings = showSettings,
                    onToggleSettings = { showSettings = !showSettings },
                    onStart = onStart,
                    onStop = onStop,
                    onManualCapture = onManualCapture,
                    onDeleteLastCapture = onDeleteLastCapture,
                    onSoundEnabledChange = onSoundEnabledChange,
                    onStableDurationChange = onStableDurationChange,
                    onMinIntervalChange = onMinIntervalChange,
                    onSensitivityChange = onSensitivityChange,
                    onBlurCheckChange = onBlurCheckChange,
                    onDarknessCheckChange = onDarknessCheckChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                )
            }
        }
    }
}

@Composable
private fun PreviewPane(
    uiState: CaptureUiState,
    previewView: PreviewView,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black)
    ) {
        if (uiState.hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            PermissionPanel(onRequestPermission = onRequestPermission)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ControlPanel(
    uiState: CaptureUiState,
    showSettings: Boolean,
    onToggleSettings: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onManualCapture: () -> Unit,
    onDeleteLastCapture: () -> Unit,
    onSoundEnabledChange: (Boolean) -> Unit,
    onStableDurationChange: (Long) -> Unit,
    onMinIntervalChange: (Long) -> Unit,
    onSensitivityChange: (Sensitivity) -> Unit,
    onBlurCheckChange: (Boolean) -> Unit,
    onDarknessCheckChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clipToBounds(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "BookAutoCapture",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = uiState.hasCameraPermission && uiState.cameraReady && !uiState.isRunning,
                onClick = onStart
            ) {
                Text("開始")
            }
            OutlinedButton(
                enabled = uiState.isRunning,
                onClick = onStop
            ) {
                Text("停止")
            }
            OutlinedButton(
                enabled = uiState.hasCameraPermission && uiState.cameraReady && !uiState.isCapturing,
                onClick = onManualCapture
            ) {
                Text("手動撮影")
            }
            OutlinedButton(
                enabled = uiState.capturedPages.isNotEmpty() && !uiState.isRunning && !uiState.isCapturing,
                onClick = onDeleteLastCapture
            ) {
                Text("最後を削除")
            }
            OutlinedButton(onClick = onToggleSettings) {
                Text(if (showSettings) "詳細設定を閉じる" else "詳細設定")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricTile(label = "撮影枚数", value = uiState.captureCount.toString())
            MetricTile(label = "現在の状態", value = uiState.statusText)
        }

        SwitchRow(
            label = "撮影音",
            checked = uiState.soundEnabled,
            onCheckedChange = onSoundEnabledChange
        )

        InfoLine(label = "保存先", value = uiState.saveFolder)
        InfoLine(label = "最後のファイル", value = uiState.lastFileName)

        CapturedPagesPanel(pages = uiState.capturedPages)

        Text(
            text = "端末仕様により、撮影音をOFFにしてもシステムのシャッター音が鳴る場合があります",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
        )

        uiState.errorMessage?.let { message ->
            Surface(
                color = Color(0xFFFFE8E1),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFF8A2E16),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (showSettings) {
            SettingsPanel(
                uiState = uiState,
                onStableDurationChange = onStableDurationChange,
                onMinIntervalChange = onMinIntervalChange,
                onSensitivityChange = onSensitivityChange,
                onBlurCheckChange = onBlurCheckChange,
                onDarknessCheckChange = onDarknessCheckChange
            )
        }
        }
    }
}

@Composable
private fun CapturedPagesPanel(pages: List<CapturedPage>) {
    if (pages.isEmpty()) return

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "このセッションの保存",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            pages.takeLast(5).forEach { page ->
                Text(
                    text = page.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (pages.size > 5) {
                Text(
                    text = "ほか ${pages.size - 5} 件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun PermissionPanel(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF191A18)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "カメラ権限が必要です",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onRequestPermission) {
                Text("許可する")
            }
        }
    }
}

@Composable
private fun RowScope.MetricTile(label: String, value: String) {
    Surface(
        modifier = Modifier.weight(1f),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SettingsPanel(
    uiState: CaptureUiState,
    onStableDurationChange: (Long) -> Unit,
    onMinIntervalChange: (Long) -> Unit,
    onSensitivityChange: (Sensitivity) -> Unit,
    onBlurCheckChange: (Boolean) -> Unit,
    onDarknessCheckChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "詳細設定",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            SettingSlider(
                label = "安定待ち時間",
                valueText = String.format(Locale.JAPAN, "%.1f秒", uiState.stableDurationMs / 1000.0),
                value = uiState.stableDurationMs / 1000f,
                range = 0.5f..2.0f,
                onValueChange = { onStableDurationChange((it * 1000).toLong()) }
            )

            SettingSlider(
                label = "最短撮影間隔",
                valueText = String.format(Locale.JAPAN, "%.1f秒", uiState.minCaptureIntervalMs / 1000.0),
                value = uiState.minCaptureIntervalMs / 1000f,
                range = 1.0f..5.0f,
                onValueChange = { onMinIntervalChange((it * 1000).toLong()) }
            )

            Text(
                text = "感度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Sensitivity.entries.forEach { sensitivity ->
                    FilterChip(
                        selected = uiState.sensitivity == sensitivity,
                        onClick = { onSensitivityChange(sensitivity) },
                        label = { Text(sensitivity.displayName()) }
                    )
                }
            }

            SwitchRow(
                label = "ブレ判定",
                checked = uiState.blurCheckEnabled,
                onCheckedChange = onBlurCheckChange
            )
            SwitchRow(
                label = "暗さ判定",
                checked = uiState.darknessCheckEnabled,
                onCheckedChange = onDarknessCheckChange
            )
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Slider(
            value = value,
            valueRange = range,
            onValueChange = onValueChange,
            colors = SliderDefaultsCompat.colors()
        )
    }
}

@Composable
private fun Sensitivity.displayName(): String {
    return when (this) {
        Sensitivity.LOW -> "低"
        Sensitivity.MEDIUM -> "中"
        Sensitivity.HIGH -> "高"
    }
}

private object SliderDefaultsCompat {
    @Composable
    fun colors() = androidx.compose.material3.SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary,
        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}
