/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import android.content.Context
import android.graphics.Bitmap
import com.example.executorch.athleticintelligence.activity.ActivityBackend
import com.example.executorch.athleticintelligence.activity.ActivityFrameResult
import com.example.executorch.athleticintelligence.activity.ActivitySession
import com.example.executorch.athleticintelligence.activity.ActivitySessionSummary
import com.example.executorch.athleticintelligence.activity.ActivityType
import com.example.executorch.athleticintelligence.activity.CameraFrame
import com.example.executorch.athleticintelligence.activity.SessionCompletion
import com.example.executorch.athleticintelligence.activity.SquatActivityFrameResult
import com.example.executorch.athleticintelligence.activity.SquatActivitySessionSummary
import com.example.executorch.athleticintelligence.model.PoseModel
import com.example.executorch.athleticintelligence.model.SimccPose
import com.example.executorch.athleticintelligence.pose.CocoPoseEstimator
import com.example.executorch.athleticintelligence.telemetry.SessionSummaryRecord
import com.example.executorch.athleticintelligence.telemetry.SessionSummaryStore
import com.example.executorch.athleticintelligence.telemetry.SessionTelemetryWriter
import com.example.executorch.athleticintelligence.telemetry.SquatMetricSummary
import com.example.executorch.athleticintelligence.telemetry.SquatRepSummary
import com.example.executorch.athleticintelligence.telemetry.SquatSessionDetails
import com.example.executorch.athleticintelligence.telemetry.SquatTelemetryAlert
import com.example.executorch.athleticintelligence.telemetry.SquatTelemetryPayload
import com.example.executorch.athleticintelligence.telemetry.TelemetryEnvelope
import com.example.executorch.athleticintelligence.telemetry.TelemetryLandmark
import com.example.executorch.athleticintelligence.telemetry.TelemetrySource
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seam over pose estimation so [SquatBackend] never depends on ExecuTorch/Android
 * in JVM tests. Production wires [RtmwSquatPoseAdapter] through
 * [SquatBackend.productionPoseEstimatorFactory]. JVM tests inject a scripted
 * fake instead, since ExecuTorch's native `.so` only ships for `arm64-v8a`.
 */
interface SquatPoseEstimator : CocoPoseEstimator

/**
 * Owns one squat session's model, [SquatFrameAnalyzer], and telemetry lifecycle,
 * and implements [ActivityBackend] end to end.
 *
 * Because [com.example.executorch.athleticintelligence.activity.ActivityFrameResult]/[ActivitySessionSummary] are
 * `sealed interface`s, Kotlin requires their *direct* implementers to live in
 * `com.example.executorch.athleticintelligence.activity` itself. [analyzeFrame] and [endSession] therefore return
 * [SquatActivityFrameResult]/[SquatActivitySessionSummary] - concrete types
 * declared in that package but built from this package's [SquatPhase],
 * [Landmarks], [TrackingStatus], and [RepRecord].
 *
 * [poseEstimatorFactory], [telemetryWriterFactory], [summaryStore], and the
 * injected clock ([nowUtcIso]) are the seams that let JVM tests exercise the
 * *real* [SquatFrameAnalyzer], [SessionSummaryStore], and (optionally) a real
 * [com.example.executorch.athleticintelligence.telemetry.JsonlTelemetryWriter] end to end, against a fake pose
 * estimator - never a mock of this class's own collaborators.
 */
class SquatBackend(
    private val view: SquatView,
    private val poseEstimatorFactory: () -> SquatPoseEstimator,
    private val telemetryWriterFactory: (ActivitySession) -> SessionTelemetryWriter,
    private val summaryStore: SessionSummaryStore,
    analyzer: SquatFrameAnalyzer = SquatFrameAnalyzer(view),
    private val nowUtcIso: () -> String = { Instant.ofEpochMilli(System.currentTimeMillis()).toString() },
    private val initializationDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionViewProvider: () -> SquatView = { view },
    private val annotatedVideoFileNameProvider: (ActivitySession) -> String? = { null },
) : ActivityBackend {

    override val activityType: ActivityType = ActivityType.SQUATS

    private val stateLock = Any()
    private var lifecycle = Lifecycle.NEW
    private var poseEstimator: SquatPoseEstimator? = null
    private var analyzer: SquatFrameAnalyzer = analyzer
    private var inFlightInferences = 0
    private var closeEstimatorWhenIdle = false
    private var activeSession: ActiveSession? = null
    private var endingSession: ActiveSession? = null
    private var latestCompletedScore: Int? = null
    private var latestPrimaryCorrection: String? = null

    override suspend fun initialize() {
        val shouldInitialize = synchronized(stateLock) {
            when (lifecycle) {
                Lifecycle.READY -> false
                Lifecycle.NEW -> {
                    lifecycle = Lifecycle.INITIALIZING
                    true
                }
                Lifecycle.INITIALIZING -> error("SquatBackend initialization is already in progress")
                Lifecycle.CLOSED -> error("SquatBackend is closed")
            }
        }
        if (!shouldInitialize) return

        val estimator = try {
            withContext(initializationDispatcher) { poseEstimatorFactory() }
        } catch (failure: Throwable) {
            synchronized(stateLock) {
                if (lifecycle == Lifecycle.INITIALIZING) lifecycle = Lifecycle.NEW
            }
            throw failure
        }

        val published = synchronized(stateLock) {
            if (lifecycle == Lifecycle.CLOSED) {
                false
            } else {
                poseEstimator = estimator
                lifecycle = Lifecycle.READY
                true
            }
        }
        if (!published) estimator.close()
    }

    override fun startSession(session: ActivitySession) {
        synchronized(stateLock) {
            check(lifecycle != Lifecycle.CLOSED) { "SquatBackend is closed" }
            check(lifecycle == Lifecycle.READY) {
                "SquatBackend is not ready: call initialize() before startSession()"
            }
            check(activeSession == null && endingSession == null) {
                "SquatBackend already owns or is ending a session"
            }
        }

        // Writer construction may throw (for example, unavailable storage).
        // Construct it before publishing session state so a failure leaves this
        // backend in the exact same idle state.
        val writer = telemetryWriterFactory(session)
        val sessionView = sessionViewProvider()
        val published = synchronized(stateLock) {
            if (
                lifecycle == Lifecycle.READY &&
                activeSession == null &&
                endingSession == null
            ) {
                analyzer = SquatFrameAnalyzer(sessionView)
                analyzer.reset()
                latestCompletedScore = null
                latestPrimaryCorrection = null
                activeSession = ActiveSession(session, writer, view = sessionView)
                true
            } else {
                false
            }
        }
        if (!published) {
            writer.abort(IllegalStateException("Session start was superseded by lifecycle change"))
            error("SquatBackend cannot publish the new session")
        }
    }

    override fun analyzeFrame(frame: CameraFrame): ActivityFrameResult {
        val inference = synchronized(stateLock) {
            check(lifecycle != Lifecycle.CLOSED) { "SquatBackend is closed" }
            check(lifecycle == Lifecycle.READY) {
                "SquatBackend is not ready: call initialize() before analyzeFrame()"
            }
            val session = activeSession
            session?.telemetryFailure?.let { throw it }
            inFlightInferences += 1
            session?.let { it.inFlightFrames += 1 }
            InferenceSnapshot(
                estimator = checkNotNull(poseEstimator),
                session = session,
            )
        }
        try {
            val landmarks = inference.estimator.run(frame.bitmap)
            val tracking =
                if (landmarks.isEmpty()) TrackingStatus.PERSON_NOT_DETECTED else TrackingStatus.TRACKED

            return synchronized(stateLock) {
                check(lifecycle != Lifecycle.CLOSED) { "SquatBackend is closed" }
                val current = activeSession
                val endingSameSession =
                    current == null &&
                        endingSession === inference.session
                if (
                    inference.session == null ||
                    (current !== inference.session && !endingSameSession)
                ) {
                    return@synchronized previewResult(landmarks, tracking)
                }
                val frameSession = checkNotNull(inference.session)
                frameSession.telemetryFailure?.let { throw it }

                val result = analyzer.processFrame(
                    landmarks,
                    frame.bitmap.width.toDouble(),
                    frame.bitmap.height.toDouble(),
                )
                result.completedRep?.let { completed ->
                    latestCompletedScore = completed.score
                    latestPrimaryCorrection = completed.primaryCorrection
                }
                emitTelemetry(frameSession, frame, result, landmarks)

                SquatActivityFrameResult(
                    phase = result.phase,
                    repCount = result.reps,
                    currentCorrection = result.activeIssues.firstOrNull()?.message ?: latestPrimaryCorrection,
                    latestScore = latestCompletedScore,
                    landmarks = landmarks,
                    tracking = tracking,
                    immediateAlerts = result.immediateAlerts,
                    activeIssues = result.activeIssues,
                    view = frameSession.view,
                )
            }
        } finally {
            completeAnalysis(inference)
        }
    }

    override suspend fun endSession(): ActivitySessionSummary {
        val ending = synchronized(stateLock) {
            check(lifecycle != Lifecycle.CLOSED) { "SquatBackend is closed" }
            check(endingSession == null) { "SquatBackend is already ending a session" }
            val active = checkNotNull(activeSession) {
                "endSession() called without an active session"
            }
            activeSession = null
            endingSession = active
            if (active.inFlightFrames == 0) active.drained.complete(Unit)
            active
        }

        try {
            ending.drained.await()
            val repRecords = synchronized(stateLock) { analyzer.completedRecords() }
            val artifact = ending.telemetryFailure?.let { failure ->
                ending.writer.fail(failure)
            } ?: ending.writer.finish()
            val session = ending.session
            val startedAtUtc = Instant.ofEpochMilli(session.startedAtEpochMs).toString()
            val endedAtUtc = nowUtcIso()
            val annotatedVideoFileName = annotatedVideoFileNameProvider(session)

            summaryStore.write(
                SessionSummaryRecord(
                    sessionId = session.id,
                    activityType = ActivityType.SQUATS,
                    telemetryFileName = artifact.fileName,
                    completion = artifact.completion,
                    frameCount = artifact.frameCount,
                    startedAtUtc = startedAtUtc,
                    endedAtUtc = endedAtUtc,
                    metrics = repSummaryMetrics(repRecords),
                    notes = repRecords.map { it.primaryCorrection }.distinct(),
                    details = SquatSessionDetails(
                        view = ending.view.wireName,
                        annotatedVideoFileName = annotatedVideoFileName,
                        reps = repRecords.map(::toPersistedRep),
                    ),
                ),
            )

            return SquatActivitySessionSummary(
                sessionId = session.id,
                telemetryFileName = artifact.fileName,
                completion = artifact.completion,
                repRecords = repRecords,
                frameCount = artifact.frameCount,
                startedAtUtc = startedAtUtc,
                endedAtUtc = endedAtUtc,
                annotatedVideoFileName = annotatedVideoFileName,
            )
        } finally {
            synchronized(stateLock) {
                if (endingSession === ending) endingSession = null
            }
        }
    }

    override fun close() {
        val resources = synchronized(stateLock) {
            if (lifecycle == Lifecycle.CLOSED) return
            lifecycle = Lifecycle.CLOSED
            val abandoned = activeSession
            activeSession = null
            val estimator = if (inFlightInferences == 0) {
                poseEstimator.also { poseEstimator = null }
            } else {
                closeEstimatorWhenIdle = true
                null
            }
            CloseResources(abandoned, estimator)
        }
        resources.abandonedSession?.writer?.abort(
            IllegalStateException("SquatBackend closed while a session was still active"),
        )
        resources.estimator?.close()
    }

    private fun emitTelemetry(
        active: ActiveSession,
        frame: CameraFrame,
        result: SquatFrameResult,
        landmarks: Landmarks,
    ) {
        val frameIndex = active.nextFrameIndex++
        val sessionElapsedMs =
            (frame.capturedAtElapsedRealtimeMs - active.session.startedElapsedRealtimeMs)
            .coerceAtLeast(0L)
        val activeRepNumber = if (result.phase != SquatPhase.STANDING) result.reps + 1 else null

        val payload = SquatTelemetryPayload(
            view = active.view.wireName,
            phase = result.phase.name,
            repCount = result.reps,
            activeRepNumber = activeRepNumber,
            alerts = result.immediateAlerts.map { alert ->
                SquatTelemetryAlert(
                    level = alert.level.name,
                    severity = alert.level.wireValue,
                    metric = alert.metric,
                    message = alert.message,
                    value = alert.value,
                    viewLabel = alert.viewLabel,
                )
            },
            landmarks = landmarks.mapValues { (_, landmark) ->
                TelemetryLandmark(landmark.xPx, landmark.yPx, landmark.visibility)
            },
            metrics = result.metrics.values,
            metricAvailability = metricAvailabilityForTelemetry(result.metrics, landmarks, active.view),
        )
        val envelope = TelemetryEnvelope(
            timestampUtc = nowUtcIso(),
            sessionElapsedMs = sessionElapsedMs,
            sourceVideoTimeMs = frame.sourceVideoTimeMs,
            frameIndex = frameIndex,
            source = TelemetrySource.LIVE_CAMERA,
            activityType = ActivityType.SQUATS,
            payload = payload,
        )

        active.writer.enqueue(envelope).exceptionOrNull()?.let { cause ->
            val failure = IllegalStateException(
                "Squat telemetry collection failed at session frame $frameIndex: " +
                    (cause.message ?: cause::class.java.simpleName),
                cause,
            )
            active.telemetryFailure = failure
            throw failure
        }
    }

    private fun metricAvailabilityForTelemetry(
        metrics: FrameMetrics,
        landmarks: Landmarks,
        view: SquatView,
    ): Map<String, String> {
        if (landmarks.isNotEmpty()) {
            return metrics.availability.mapValues { (_, availability) -> availability.name }
        }
        val unavailableFromCoco = when (view) {
            SquatView.FRONT -> emptySet()
            SquatView.SIDE -> setOf("heel_lift", "knee_over_toes")
            SquatView.BACK -> setOf("heel_asymmetry")
        }
        return metrics.availability.keys.associateWith { metric ->
            if (metric in unavailableFromCoco) {
                MetricAvailability.NOT_APPLICABLE.name
            } else {
                MetricAvailability.INSUFFICIENT_DATA.name
            }
        }
    }

    private fun previewResult(
        landmarks: Landmarks,
        tracking: TrackingStatus,
    ): SquatActivityFrameResult =
        SquatActivityFrameResult(
            phase = analyzer.phase,
            repCount = analyzer.reps,
            currentCorrection = latestPrimaryCorrection,
            latestScore = latestCompletedScore,
            landmarks = landmarks,
            tracking = tracking,
        )

    private fun completeAnalysis(inference: InferenceSnapshot) {
        val estimatorToClose = synchronized(stateLock) {
            inFlightInferences -= 1
            check(inFlightInferences >= 0)
            inference.session?.let { session ->
                session.inFlightFrames -= 1
                check(session.inFlightFrames >= 0)
                if (session.inFlightFrames == 0) session.drained.complete(Unit)
            }
            if (closeEstimatorWhenIdle && inFlightInferences == 0) {
                closeEstimatorWhenIdle = false
                poseEstimator.also { poseEstimator = null }
            } else {
                null
            }
        }
        estimatorToClose?.close()
    }

    private fun toPersistedRep(rep: RepRecord): SquatRepSummary =
        SquatRepSummary(
            repNumber = rep.repNumber,
            grade = rep.grade.name,
            minKneeAngleDeg = rep.minKneeAngleDeg,
            score = rep.score,
            confidenceCoverage = rep.confidenceCoverage,
            primaryCorrection = rep.primaryCorrection,
            secondaryCorrections = rep.secondaryCorrections,
            issueSeverities = rep.issues.mapValues { (_, severity) -> severity.wireValue },
            issueMessages = rep.issueMessages,
            metricSummaries = rep.metricSummaries.mapValues { (_, summary) ->
                SquatMetricSummary(
                    metric = summary.metric,
                    validFrames = summary.validFrames,
                    totalFrames = summary.totalFrames,
                    mean = summary.mean,
                    minValue = summary.minValue,
                    maxValue = summary.maxValue,
                    warningPersistence = summary.warningPersistence,
                    dangerPersistence = summary.dangerPersistence,
                    severityArea = summary.severityArea,
                    contribution = summary.contribution,
                    message = summary.message,
                    status = summary.status.name,
                )
            },
        )

    private fun repSummaryMetrics(repRecords: List<RepRecord>): Map<String, Double> {
        if (repRecords.isEmpty()) return emptyMap()
        return mapOf(
            "rep_count" to repRecords.size.toDouble(),
            "average_score" to repRecords.map { it.score }.average(),
            "good_reps" to repRecords.count { it.grade == RepGrade.GOOD }.toDouble(),
            "fair_reps" to repRecords.count { it.grade == RepGrade.FAIR }.toDouble(),
            "bad_reps" to repRecords.count { it.grade == RepGrade.BAD }.toDouble(),
        )
    }

    private enum class Lifecycle { NEW, INITIALIZING, READY, CLOSED }

    private class ActiveSession(
        val session: ActivitySession,
        val writer: SessionTelemetryWriter,
        val view: SquatView,
        var nextFrameIndex: Long = 0L,
        var telemetryFailure: Throwable? = null,
        var inFlightFrames: Int = 0,
        val drained: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private data class InferenceSnapshot(
        val estimator: SquatPoseEstimator,
        val session: ActiveSession?,
    )

    private data class CloseResources(
        val abandonedSession: ActiveSession?,
        val estimator: SquatPoseEstimator?,
    )

    companion object {
        /**
         * Production [SquatPoseEstimator] factory: constructs RTMW-l body-133
         * through [SimccPose], configured for its 384-high by 288-wide input.
         * [modelFactory] is a host-test seam; production callers use its default.
         */
        fun productionPoseEstimatorFactory(
            context: Context,
            assetName: String = RtmwSquatPoseAdapter.MODEL_ASSET,
            modelFactory: (Context, String, Int, Int, Int) -> PoseModel =
                { modelContext, modelAsset, numKeypoints, inputWidth, inputHeight ->
                    SimccPose(
                        modelContext,
                        modelAsset,
                        numKeypoints,
                        inputWidth,
                        inputHeight,
                    )
                },
        ): () -> SquatPoseEstimator = {
            val model = try {
                modelFactory(
                    context,
                    assetName,
                    RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT,
                    RtmwSquatPoseAdapter.MODEL_INPUT_WIDTH,
                    RtmwSquatPoseAdapter.MODEL_INPUT_HEIGHT,
                )
            } catch (failure: Exception) {
                throw IllegalStateException(
                    "Failed to load RTMW-l squat model asset '$assetName'",
                    failure,
                )
            }
            RtmwSquatPoseAdapter(model)
        }
    }
}
