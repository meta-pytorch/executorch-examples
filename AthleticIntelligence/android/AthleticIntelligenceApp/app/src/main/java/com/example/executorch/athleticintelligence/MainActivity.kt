/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.executorch.athleticintelligence.activity.ActivitySession
import com.example.executorch.athleticintelligence.activity.ActivityType
import com.example.executorch.athleticintelligence.activity.CameraFrame
import com.example.executorch.athleticintelligence.activity.SquatActivityFrameResult
import com.example.executorch.athleticintelligence.capture.CameraRecoveryAction
import com.example.executorch.athleticintelligence.capture.SquatOrchestrationEvent
import com.example.executorch.athleticintelligence.capture.SquatOrchestrationState
import com.example.executorch.athleticintelligence.capture.SquatSessionLifecycle
import com.example.executorch.athleticintelligence.capture.reduceSquatOrchestration
import com.example.executorch.athleticintelligence.history.buildActivityHistoryPresentation
import com.example.executorch.athleticintelligence.squat.SquatBackend
import com.example.executorch.athleticintelligence.squat.SquatPhase
import com.example.executorch.athleticintelligence.squat.SquatView
import com.example.executorch.athleticintelligence.squat.TrackingStatus
import com.example.executorch.athleticintelligence.squat.toAnnotationFrame
import com.example.executorch.athleticintelligence.telemetry.JsonlTelemetryWriter
import com.example.executorch.athleticintelligence.telemetry.SessionExportArtifact
import com.example.executorch.athleticintelligence.telemetry.SessionExportStore
import com.example.executorch.athleticintelligence.telemetry.SessionSummaryRecord
import com.example.executorch.athleticintelligence.telemetry.SessionSummaryStore
import com.example.executorch.athleticintelligence.ui.DashboardDestination
import com.example.executorch.athleticintelligence.ui.MetaAthleticTheme
import com.example.executorch.athleticintelligence.ui.SquatFeatureActions
import com.example.executorch.athleticintelligence.ui.SquatFeatureScreen
import com.example.executorch.athleticintelligence.ui.SquatFeatureUiState
import com.example.executorch.athleticintelligence.ui.SquatPoseUiState
import com.example.executorch.athleticintelligence.ui.TimelineSegment
import com.example.executorch.athleticintelligence.ui.TrendPeriod
import com.example.executorch.athleticintelligence.ui.toPoseUiState
import com.example.executorch.athleticintelligence.video.AnnotatedVideoArtifact
import com.example.executorch.athleticintelligence.video.AnnotatedVideoRecorder
import com.example.executorch.athleticintelligence.vision.toUprightBitmap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private val backendScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val analyzerExecutor = Executors.newSingleThreadExecutor()
    private val liveBusy = AtomicBoolean(false)
    private val cameraFrameIndex = AtomicLong()

    private lateinit var backend: SquatBackend
    private lateinit var summaryStore: SessionSummaryStore
    private lateinit var sessionExportStore: SessionExportStore
    private lateinit var annotatedVideoRecorder: AnnotatedVideoRecorder
    private val completedVideos = ConcurrentHashMap<String, AnnotatedVideoArtifact>()

    private var orchestration by mutableStateOf(SquatOrchestrationState())
    @Volatile private var activeGeneration = 0L
    @Volatile private var activityTearingDown = false

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraPreview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var previewView: PreviewView? = null
    private var pendingStartCamera = false
    private var permissionsRequested = false

    private var selectedSquatView by mutableStateOf(SquatView.SIDE)
    private var squatPhase by mutableStateOf(SquatPhase.STANDING)
    private var squatRepCount by mutableStateOf(0)
    private var squatCorrection by mutableStateOf<String?>(null)
    private var squatScore by mutableStateOf<Int?>(null)
    private var squatTracking by mutableStateOf(TrackingStatus.PERSON_NOT_DETECTED)
    private var squatPose by mutableStateOf<SquatPoseUiState?>(null)
    private var collectionError by mutableStateOf<String?>(null)
    private var activeSession: ActivitySession? = null
    private var endSessionJob: Job? = null
    private var squatHistory by mutableStateOf(
        buildActivityHistoryPresentation(ActivityType.SQUATS, emptyList()),
    )
    private var squatSummaries by mutableStateOf<List<SessionSummaryRecord>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        summaryStore = SessionSummaryStore(filesDir.toPath().resolve("session-summaries"))
        val telemetryDirectory = filesDir.toPath().resolve("telemetry")
        val videoDirectory = filesDir.toPath().resolve("squat-videos")
        annotatedVideoRecorder = AnnotatedVideoRecorder(videoDirectory)
        sessionExportStore = SessionExportStore(
            summaryDirectory = filesDir.toPath().resolve("session-summaries"),
            telemetryDirectory = telemetryDirectory,
            exportDirectory = cacheDir.toPath().resolve("session-exports"),
            videoDirectory = videoDirectory,
        )
        backend = SquatBackend(
            view = SquatView.SIDE,
            poseEstimatorFactory = SquatBackend.productionPoseEstimatorFactory(applicationContext),
            telemetryWriterFactory = { session ->
                JsonlTelemetryWriter(
                    outputDirectory = telemetryDirectory,
                    fileName = telemetryFileName(session),
                )
            },
            summaryStore = summaryStore,
            sessionViewProvider = { selectedSquatView },
            annotatedVideoFileNameProvider = { session ->
                completedVideos[session.id]?.fileName
            },
        )

        dispatch(SquatOrchestrationEvent.Started(cameraPermissionGranted()))
        initializeBackend()
        loadSquatHistory()

        setContent {
            MetaAthleticTheme {
                var selectedTimelineSegment by rememberSaveable {
                    mutableStateOf(TimelineSegment.SESSION)
                }
                var selectedTrendPeriod by rememberSaveable {
                    mutableStateOf(TrendPeriod.WEEK)
                }
                SquatFeatureScreen(
                    state = SquatFeatureUiState(
                        selectedDestination = orchestration.destination,
                        timelineSegment = selectedTimelineSegment,
                        backendReady = orchestration.backendReady,
                        sessionActive = orchestration.sessionLifecycle != SquatSessionLifecycle.IDLE,
                        phase = squatPhase,
                        repCount = squatRepCount,
                        currentCorrection = squatCorrection,
                        latestScore = squatScore,
                        tracking = squatTracking,
                        history = squatHistory,
                        pose = squatPose,
                        collectionError = collectionError,
                        sessionEnding = orchestration.sessionLifecycle == SquatSessionLifecycle.ENDING,
                        cameraStatus = orchestration.cameraStatusText,
                        cameraRecoveryLabel = cameraRecoveryLabel(),
                        selectedView = selectedSquatView,
                        canChangeView = orchestration.sessionLifecycle == SquatSessionLifecycle.IDLE,
                        trendPeriod = selectedTrendPeriod,
                        summaries = squatSummaries,
                    ),
                    actions = SquatFeatureActions(
                        selectDestination = ::selectDestination,
                        selectTimelineSegment = { selectedTimelineSegment = it },
                        startSession = ::startSquatSession,
                        endSession = ::endSquatSession,
                        recoverCamera = ::recoverCamera,
                        selectView = ::selectSquatView,
                        exportSession = ::exportSquatSession,
                        selectTrendPeriod = { selectedTrendPeriod = it },
                    ),
                    cameraContent = { CameraContent() },
                )
            }
        }

        if (cameraPermissionGranted()) {
            startCameraWhenReady()
        } else {
            pendingStartCamera = true
            requestCameraPermission()
        }
    }

    private fun initializeBackend() {
        val generation = activeGeneration
        backendScope.launch(Dispatchers.IO) {
            runCatching { backend.initialize() }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        if (accepts(generation)) {
                            dispatch(SquatOrchestrationEvent.BackendReady)
                        }
                    }
                }
                .onFailure { failure ->
                    withContext(Dispatchers.Main) {
                        if (accepts(generation)) {
                            collectionError = failure.message ?: "Unable to load squat model"
                        }
                    }
                }
        }
    }

    private fun dispatch(event: SquatOrchestrationEvent) {
        orchestration = reduceSquatOrchestration(orchestration, event)
        activeGeneration = orchestration.generation
    }

    private fun accepts(generation: Long): Boolean =
        !activityTearingDown && generation == activeGeneration

    private fun selectDestination(destination: DashboardDestination) {
        dispatch(SquatOrchestrationEvent.DestinationSelected(destination))
    }

    private fun selectSquatView(view: SquatView) {
        if (orchestration.sessionLifecycle == SquatSessionLifecycle.IDLE) {
            selectedSquatView = view
        }
    }

    private fun startSquatSession() {
        if (!orchestration.canStartSquat) return
        val session = ActivitySession(
            id = UUID.randomUUID().toString(),
            activityType = ActivityType.SQUATS,
            startedAtEpochMs = System.currentTimeMillis(),
            startedElapsedRealtimeMs = SystemClock.elapsedRealtime(),
        )
        runCatching { backend.startSession(session) }
            .onSuccess {
                activeSession = session
                completedVideos.remove(session.id)
                val recordingResult = annotatedVideoRecorder.start(session.id)
                dispatch(SquatOrchestrationEvent.SessionStarted)
                collectionError = recordingResult.exceptionOrNull()?.let { failure ->
                    failure.message ?: "Annotated video recording unavailable"
                }
            }
            .onFailure { failure ->
                collectionError = failure.message ?: "Unable to start squat session"
            }
    }

    private fun endSquatSession() {
        if (orchestration.sessionLifecycle != SquatSessionLifecycle.ACTIVE) return
        val session = activeSession
        val generation = activeGeneration
        dispatch(SquatOrchestrationEvent.SessionEnding)
        endSessionJob = backendScope.launch(Dispatchers.IO) {
            annotatedVideoRecorder.stop()
                .onSuccess { artifact ->
                    if (session != null && artifact != null) completedVideos[session.id] = artifact
                }
                .onFailure { failure -> reportCollectionFailure(generation, failure, "Annotated video recording failed") }

            runCatching { backend.endSession() }
                .onSuccess { loadSquatHistory(generation) }
                .onFailure { failure -> reportCollectionFailure(generation, failure, "Unable to end squat session") }

            withContext(Dispatchers.Main) {
                if (accepts(generation)) {
                    activeSession = null
                    dispatch(SquatOrchestrationEvent.SessionEnded)
                }
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (endSessionJob === job) endSessionJob = null
            }
        }
    }

    private suspend fun reportCollectionFailure(
        generation: Long,
        failure: Throwable,
        fallback: String,
    ) {
        withContext(Dispatchers.Main) {
            if (accepts(generation)) collectionError = failure.message ?: fallback
        }
    }

    private fun loadSquatHistory(generation: Long = activeGeneration) {
        backendScope.launch(Dispatchers.IO) {
            val summaries = runCatching { summaryStore.list(ActivityType.SQUATS) }.getOrDefault(emptyList())
            val presentation = buildActivityHistoryPresentation(ActivityType.SQUATS, summaries)
            withContext(Dispatchers.Main) {
                if (accepts(generation)) {
                    squatSummaries = summaries
                    squatHistory = presentation
                }
            }
        }
    }

    private fun exportSquatSession(sessionId: String) {
        val generation = activeGeneration
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching { sessionExportStore.exportSession(sessionId) }
            withContext(Dispatchers.Main) {
                if (!accepts(generation)) return@withContext
                result.onSuccess(::shareExportArtifact).onFailure { failure ->
                    toast(failure.message ?: "Unable to export squat session")
                }
            }
        }
    }

    private fun shareExportArtifact(artifact: SessionExportArtifact) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", artifact.file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = artifact.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, artifact.displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Export squat session"))
    }

    private fun startCameraWhenReady() {
        if (isFinishing || isDestroyed || activityTearingDown) return
        val preview = previewView
        if (preview == null || !preview.isAttachedToWindow || !cameraPermissionGranted()) {
            pendingStartCamera = true
            return
        }
        pendingStartCamera = false
        startCamera(preview)
    }

    private fun startCamera(targetPreview: PreviewView) {
        val generation = activeGeneration
        dispatch(SquatOrchestrationEvent.CameraBindingStarted)
        val future = runCatching { ProcessCameraProvider.getInstance(this) }.getOrElse {
            dispatch(SquatOrchestrationEvent.CameraBindingFailed)
            return
        }
        future.addListener({
            if (
                !accepts(generation) ||
                previewView !== targetPreview ||
                !targetPreview.isAttachedToWindow
            ) {
                return@addListener
            }
            runCatching {
                val provider = future.get()
                val rotation = targetPreview.display.rotation
                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build().also {
                    it.setSurfaceProvider(targetPreview.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.setAnalyzer(analyzerExecutor, ::onLiveFrame) }
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
                cameraProvider = provider
                cameraPreview = preview
                imageAnalysis = analysis
                dispatch(SquatOrchestrationEvent.CameraBound)
            }.onFailure {
                cameraPreview = null
                imageAnalysis?.clearAnalyzer()
                imageAnalysis = null
                dispatch(SquatOrchestrationEvent.CameraBindingFailed)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        previewView?.post {
            val rotation = when (newConfig.orientation) {
                Configuration.ORIENTATION_LANDSCAPE -> Surface.ROTATION_90
                Configuration.ORIENTATION_PORTRAIT -> Surface.ROTATION_0
                else -> previewView?.display?.rotation ?: return@post
            }
            cameraPreview?.targetRotation = rotation
            imageAnalysis?.targetRotation = rotation
        }
    }

    private fun onLiveFrame(image: ImageProxy) {
        if (!orchestration.shouldAnalyzeFrames || !liveBusy.compareAndSet(false, true)) {
            image.close()
            return
        }
        val generation = activeGeneration
        try {
            val bitmap = image.toUprightBitmap()
            val frame = CameraFrame(
                bitmap = bitmap,
                frameIndex = cameraFrameIndex.getAndIncrement(),
                capturedAtEpochMs = System.currentTimeMillis(),
                capturedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            )
            val result = backend.analyzeFrame(frame) as SquatActivityFrameResult
            submitAnnotatedFrame(bitmap, frame, result)
            runOnUiThread {
                if (!accepts(generation) || !orchestration.shouldAnalyzeFrames) return@runOnUiThread
                squatPhase = result.phase
                squatRepCount = result.repCount
                squatCorrection = result.currentCorrection
                squatScore = result.latestScore
                squatTracking = result.tracking
                squatPose = result.toPoseUiState(bitmap.width, bitmap.height)
            }
        } catch (failure: Throwable) {
            runOnUiThread {
                if (accepts(generation)) {
                    collectionError = failure.message ?: "Live squat analysis stopped"
                }
            }
        } finally {
            liveBusy.set(false)
            image.close()
        }
    }

    private fun submitAnnotatedFrame(
        bitmap: Bitmap,
        frame: CameraFrame,
        result: SquatActivityFrameResult,
    ) {
        val session = activeSession ?: return
        if (orchestration.sessionLifecycle != SquatSessionLifecycle.ACTIVE) return
        annotatedVideoRecorder.submit(
            bitmap = bitmap,
            annotation = result.toAnnotationFrame(bitmap.width, bitmap.height),
            presentationTimeUs =
                (frame.capturedAtElapsedRealtimeMs - session.startedElapsedRealtimeMs)
                    .coerceAtLeast(0L) * 1_000L,
        )
    }

    @Composable
    private fun CameraContent() {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    FrameLayout(context).apply {
                        val preview = PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            layoutParams = matchParentLayoutParams()
                        }
                        addView(preview)
                        previewView = preview
                        val listener = object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(view: View) = Unit
                            override fun onViewDetachedFromWindow(view: View) {
                                if (previewView === preview) previewView = null
                            }
                        }
                        tag = listener
                        addOnAttachStateChangeListener(listener)
                        if (cameraPermissionGranted()) {
                            post { if (previewView === preview) startCameraWhenReady() }
                        } else {
                            pendingStartCamera = true
                        }
                    }
                },
                onRelease = { root ->
                    (root.tag as? View.OnAttachStateChangeListener)?.let {
                        root.removeOnAttachStateChangeListener(it)
                    }
                    root.tag = null
                    val releasedPreview = root.getChildAt(0) as PreviewView
                    if (previewView === releasedPreview) previewView = null
                },
            )
        }
    }

    private fun cameraRecoveryLabel(): String? = when (orchestration.cameraRecoveryAction) {
        CameraRecoveryAction.NONE -> null
        CameraRecoveryAction.REQUEST_PERMISSION -> "Allow camera"
        CameraRecoveryAction.OPEN_SETTINGS -> "Open Settings"
        CameraRecoveryAction.RETRY_BINDING -> "Retry camera"
    }

    private fun recoverCamera() {
        when (orchestration.cameraRecoveryAction) {
            CameraRecoveryAction.NONE -> Unit
            CameraRecoveryAction.REQUEST_PERMISSION -> requestCameraPermission()
            CameraRecoveryAction.OPEN_SETTINGS -> openAppSettings()
            CameraRecoveryAction.RETRY_BINDING -> startCameraWhenReady()
        }
    }

    private fun cameraPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        permissionsRequested = true
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE,
        )
    }

    private fun permissionPermanentlyDenied(): Boolean =
        permissionsRequested &&
            !cameraPermissionGranted() &&
            !ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        runCatching { startActivity(intent) }.onFailure {
            toast("Open Settings and enable camera access")
        }
    }

    override fun onResume() {
        super.onResume()
        if (cameraPermissionGranted() &&
            orchestration.cameraRecoveryAction != CameraRecoveryAction.NONE
        ) {
            startCameraWhenReady()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != CAMERA_PERMISSION_REQUEST_CODE) return
        if (cameraPermissionGranted()) {
            if (pendingStartCamera || previewView != null) startCameraWhenReady()
        } else {
            pendingStartCamera = false
            dispatch(SquatOrchestrationEvent.CameraPermissionDenied(permissionPermanentlyDenied()))
        }
    }

    override fun onDestroy() {
        activityTearingDown = true
        activeGeneration += 1L
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        cameraPreview = null
        cameraProvider?.unbindAll()
        cameraProvider = null
        analyzerExecutor.shutdown()

        val pendingEnd = endSessionJob
        backendScope.launch(Dispatchers.IO) {
            pendingEnd?.join()
            annotatedVideoRecorder.abort()
            backend.close()
        }.invokeOnCompletion { backendScope.cancel() }
        super.onDestroy()
    }

    private fun telemetryFileName(session: ActivitySession): String =
        "squats_${session.startedAtEpochMs}_${session.id}.jsonl"

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 10

        private fun matchParentLayoutParams() = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
    }
}
