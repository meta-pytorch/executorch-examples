/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import android.graphics.Bitmap
import com.example.executorch.athleticintelligence.activity.ActivityFrameResult
import com.example.executorch.athleticintelligence.activity.ActivitySession
import com.example.executorch.athleticintelligence.activity.ActivityType
import com.example.executorch.athleticintelligence.activity.CameraFrame
import com.example.executorch.athleticintelligence.activity.SessionCompletion
import com.example.executorch.athleticintelligence.activity.SquatActivityFrameResult
import com.example.executorch.athleticintelligence.activity.SquatActivitySessionSummary
import com.example.executorch.athleticintelligence.telemetry.JsonlTelemetryWriter
import com.example.executorch.athleticintelligence.telemetry.SessionSummaryStore
import com.example.executorch.athleticintelligence.telemetry.SessionTelemetryWriter
import com.example.executorch.athleticintelligence.telemetry.SquatSessionDetails
import com.example.executorch.athleticintelligence.telemetry.SquatTelemetryPayload
import com.example.executorch.athleticintelligence.telemetry.TelemetryArtifact
import com.example.executorch.athleticintelligence.telemetry.TelemetryCodec
import com.example.executorch.athleticintelligence.telemetry.TelemetryEnvelope
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

/**
 * TDD coverage for [SquatBackend]'s live session lifecycle: initialize
 * readiness, Start/preview/persistence boundaries, full-frame telemetry
 * (including missing-person frames), End's drain-before-summary ordering,
 * fail-fast telemetry failure, and close's exactly-once resource teardown.
 *
 * Exercises the *real* [SquatFrameAnalyzer] (unmodified squat mechanics/COCO
 * NOT_APPLICABLE behavior), the *real* [SessionSummaryStore] (real files under
 * a temp directory), and - in the round-trip test - the *real*
 * [JsonlTelemetryWriter] + [TelemetryCodec]. Only the pose estimator (which
 * requires ExecuTorch/ARM64) and, for precise failure-injection control, the
 * telemetry writer are faked.
 */
class SquatBackendTest {

    private val createdDirectories = mutableListOf<Path>()

    @After
    fun cleanUp() {
        createdDirectories.forEach { dir ->
            if (Files.exists(dir)) {
                Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun bitmap(w: Int = 300, h: Int = 400): Bitmap {
        val bmp = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bmp.width).thenReturn(w)
        Mockito.`when`(bmp.height).thenReturn(h)
        return bmp
    }

    private fun frame(index: Long, elapsedMs: Long, epochMs: Long = elapsedMs): CameraFrame =
        CameraFrame(
            bitmap = bitmap(),
            frameIndex = index,
            capturedAtEpochMs = epochMs,
            capturedAtElapsedRealtimeMs = elapsedMs,
        )

    private fun session(id: String = "s1", startedElapsedMs: Long = 0L, startedEpochMs: Long = 0L) =
        ActivitySession(id, ActivityType.SQUATS, startedEpochMs, startedElapsedMs)

    /**
     * Symmetric front-view landmarks whose interior knee angle (hip->knee->ankle)
     * is exactly [angleDeg] - the same construction [SquatFrameAnalyzerTest]
     * uses, reused here so backend tests still drive the *real*
     * [SquatStateMachine]/geometry, not a stub of analyzer behavior.
     */
    private fun landmarksForKneeAngle(angleDeg: Double): Landmarks {
        val segmentLen = 100.0
        val theta = Math.toRadians(180.0 - angleDeg)
        val kneeY = segmentLen
        val ankleX = segmentLen * Math.sin(theta)
        val ankleY = segmentLen + segmentLen * Math.cos(theta)
        return mapOf(
            "l_shoulder" to Landmark(-10.0, -80.0, 0.99),
            "r_shoulder" to Landmark(10.0, -80.0, 0.99),
            "l_hip" to Landmark(-10.0, 0.0, 0.99),
            "r_hip" to Landmark(10.0, 0.0, 0.99),
            "l_knee" to Landmark(-10.0, kneeY, 0.99),
            "r_knee" to Landmark(10.0, kneeY, 0.99),
            "l_ankle" to Landmark(ankleX - 10.0, ankleY, 0.99),
            "r_ankle" to Landmark(ankleX + 10.0, ankleY, 0.99),
        )
    }

    /** Full FRONT-view rep cycle known (from [SquatFrameAnalyzerTest]) to complete exactly one rep. */
    private val oneRepAngles = listOf(170.0, 155.0, 120.0, 95.0, 120.0, 155.0, 170.0)

    private class ScriptedPoseEstimator(private val script: List<Landmarks>) : SquatPoseEstimator {
        var closeCount = 0
        var runCount = 0

        override fun run(bitmap: Bitmap): Landmarks {
            val result = if (runCount < script.size) script[runCount] else script.lastOrNull().orEmpty()
            runCount++
            return result
        }

        override fun close() {
            closeCount++
        }
    }

    private class RecordingTelemetryWriter(
        private val failAtAttempt: Int? = null,
        private val events: MutableList<String>? = null,
    ) : SessionTelemetryWriter {
        val enqueued = mutableListOf<TelemetryEnvelope>()
        val attempted = mutableListOf<TelemetryEnvelope>()
        var finishCalls = 0
        var failCalls = 0
        var abortCalls = 0
        var failedCause: Throwable? = null

        override fun enqueue(envelope: TelemetryEnvelope): Result<Unit> {
            attempted.add(envelope)
            if (attempted.size == failAtAttempt) {
                return Result.failure(IllegalStateException("simulated telemetry queue failure"))
            }
            enqueued.add(envelope)
            return Result.success(Unit)
        }

        override suspend fun finish(): TelemetryArtifact {
            finishCalls++
            events?.add("writer_finish")
            return TelemetryArtifact(
                fileName = "squat-test.jsonl",
                frameCount = enqueued.size.toLong(),
                completion = if (failedCause == null) SessionCompletion.COMPLETE else SessionCompletion.INCOMPLETE,
                errorMessage = failedCause?.message,
            )
        }

        override suspend fun fail(cause: Throwable): TelemetryArtifact {
            failCalls++
            failedCause = cause
            events?.add("writer_fail")
            return TelemetryArtifact(
                fileName = "squat-test.jsonl",
                frameCount = enqueued.size.toLong(),
                completion = SessionCompletion.INCOMPLETE,
                errorMessage = cause.message,
            )
        }

        override fun abort(cause: Throwable) {
            abortCalls++
            failedCause = cause
            events?.add("writer_abort")
        }
    }

    private fun tempSummaryStore(onBeforeRename: ((Path, Path) -> Unit)? = null): SessionSummaryStore {
        val dir = Files.createTempDirectory("squat-backend-summaries")
        createdDirectories.add(dir)
        return SessionSummaryStore(directory = dir, onBeforeRename = onBeforeRename)
    }

    private fun newBackend(
        estimator: ScriptedPoseEstimator,
        writer: RecordingTelemetryWriter = RecordingTelemetryWriter(),
        summaryStore: SessionSummaryStore = tempSummaryStore(),
        view: SquatView = SquatView.FRONT,
    ): SquatBackend = SquatBackend(
        view = view,
        poseEstimatorFactory = { estimator },
        telemetryWriterFactory = { writer },
        summaryStore = summaryStore,
    )

    // ---- initialize readiness ----

    @Test
    fun `analyzeFrame before initialize throws`() {
        val backend = newBackend(ScriptedPoseEstimator(listOf()))

        assertThrows(IllegalStateException::class.java) {
            backend.analyzeFrame(frame(0, 0))
        }
    }

    @Test
    fun `startSession before initialize throws`() {
        val backend = newBackend(ScriptedPoseEstimator(listOf()))

        assertThrows(IllegalStateException::class.java) {
            backend.startSession(session())
        }
    }

    @Test
    fun `initialize loads the pose estimator exactly once and makes the backend ready`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(listOf(landmarksForKneeAngle(170.0)))
        var factoryInvocations = 0
        val backend = SquatBackend(
            view = SquatView.FRONT,
            poseEstimatorFactory = { factoryInvocations++; estimator },
            telemetryWriterFactory = { RecordingTelemetryWriter() },
            summaryStore = tempSummaryStore(),
        )

        backend.initialize()
        backend.initialize() // idempotent: must not reload

        assertEquals(1, factoryInvocations)
        backend.analyzeFrame(frame(0, 0)) // no longer throws a readiness error
    }

    // ---- preview before Start: inference without persistence ----

    @Test
    fun `preview frames before Start run inference but never open a telemetry writer`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(listOf(landmarksForKneeAngle(170.0)))
        var writerFactoryInvocations = 0
        val backend = SquatBackend(
            view = SquatView.FRONT,
            poseEstimatorFactory = { estimator },
            telemetryWriterFactory = { writerFactoryInvocations++; RecordingTelemetryWriter() },
            summaryStore = tempSummaryStore(),
        )
        backend.initialize()

        val result = backend.analyzeFrame(frame(0, 0)) as SquatActivityFrameResult

        assertEquals(0, writerFactoryInvocations)
        assertEquals(SquatPhase.STANDING, result.phase)
        assertEquals(0, result.repCount)
        assertEquals(TrackingStatus.TRACKED, result.tracking)
        assertEquals(1, estimator.runCount)
    }

    // ---- every active-session frame is enqueued, including missing-person frames ----

    @Test
    fun `every active-session frame is enqueued including missing-person frames`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(
            listOf(landmarksForKneeAngle(170.0), emptyMap(), landmarksForKneeAngle(150.0)),
        )
        val writer = RecordingTelemetryWriter()
        val backend = newBackend(estimator, writer = writer)
        backend.initialize()
        backend.startSession(session())

        val results = listOf(
            backend.analyzeFrame(frame(0, 0)) as SquatActivityFrameResult,
            backend.analyzeFrame(frame(1, 33)) as SquatActivityFrameResult,
            backend.analyzeFrame(frame(2, 66)) as SquatActivityFrameResult,
        )

        assertEquals(3, writer.enqueued.size)
        assertEquals(listOf(0L, 1L, 2L), writer.enqueued.map { it.frameIndex })
        assertEquals(TrackingStatus.TRACKED, results[0].tracking)
        assertEquals(TrackingStatus.PERSON_NOT_DETECTED, results[1].tracking)
        assertTrue(
            "missing-person landmarks must still be recorded as an empty map, not skipped",
            results[1].landmarks.isEmpty(),
        )
        val missingPersonPayload = writer.enqueued[1].payload as SquatTelemetryPayload
        assertTrue(missingPersonPayload.landmarks.isEmpty())
        assertEquals(
            "INSUFFICIENT_DATA",
            missingPersonPayload.metricAvailability["knee_valgus_l"],
        )
    }

    @Test
    fun `session-elapsed telemetry timing derives from elapsed-realtime not wall clock`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(
            listOf(landmarksForKneeAngle(170.0), landmarksForKneeAngle(170.0)),
        )
        val writer = RecordingTelemetryWriter()
        val backend = newBackend(estimator, writer = writer)
        backend.initialize()
        backend.startSession(session(startedElapsedMs = 1_000L))

        backend.analyzeFrame(frame(0, elapsedMs = 1_000L))
        backend.analyzeFrame(frame(1, elapsedMs = 1_500L))

        assertEquals(listOf(0L, 500L), writer.enqueued.map { it.sessionElapsedMs })
    }

    @Test
    fun `COCO landmarks leave heel_lift and knee_over_toes NOT_APPLICABLE out of telemetry`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(listOf(landmarksForKneeAngle(150.0)))
        val writer = RecordingTelemetryWriter()
        // Side view's heel_lift/knee_over_toes need heel/foot landmarks that
        // COCO-17 (Coco17Adapter) never supplies - see extractFrameMetrics.
        val backend = newBackend(estimator, writer = writer, view = SquatView.SIDE)
        backend.initialize()
        backend.startSession(session())

        backend.analyzeFrame(frame(0, 0))

        val payload = writer.enqueued.single().payload as SquatTelemetryPayload
        assertFalse(
            "NOT_APPLICABLE metrics must never be synthesized into telemetry",
            payload.metrics.containsKey("heel_lift"),
        )
        assertFalse(payload.metrics.containsKey("knee_over_toes"))
        assertTrue(payload.metrics.containsKey("depth_angle"))
        assertEquals("NOT_APPLICABLE", payload.metricAvailability["heel_lift"])
        assertEquals("NOT_APPLICABLE", payload.metricAvailability["knee_over_toes"])
        assertEquals("AVAILABLE", payload.metricAvailability["depth_angle"])
    }

    @Test
    fun `squat telemetry records structured alerts with numeric severity`(): Unit = runBlocking {
        val valgus = landmarksForKneeAngle(145.0).toMutableMap().apply {
            this["l_knee"] = Landmark(30.0, 100.0, 0.99)
            this["l_ankle"] = Landmark(-10.0, 200.0, 0.99)
        }
        val writer = RecordingTelemetryWriter()
        val backend = newBackend(ScriptedPoseEstimator(listOf(valgus)), writer = writer)
        backend.initialize()
        backend.startSession(session())

        backend.analyzeFrame(frame(0, 0))

        val alert = (writer.enqueued.single().payload as SquatTelemetryPayload)
            .alerts.single { it.metric == "valgus_l" }
        assertEquals("DANGER", alert.level)
        assertEquals(2, alert.severity)
        assertEquals("Front", alert.viewLabel)
        assertTrue(alert.value > 0.0)
    }

    // ---- Start resets the analyzer and every session-scoped counter ----

    @Test
    fun `Start resets the analyzer phase reps and frame counters for a new session`(): Unit = runBlocking {
        val script = (oneRepAngles + 170.0).map { landmarksForKneeAngle(it) }
        val estimator = ScriptedPoseEstimator(script)
        var writer = RecordingTelemetryWriter()
        val backend = SquatBackend(
            view = SquatView.FRONT,
            poseEstimatorFactory = { estimator },
            telemetryWriterFactory = { writer },
            summaryStore = tempSummaryStore(),
        )
        backend.initialize()
        backend.startSession(session(id = "s1"))
        oneRepAngles.indices.forEach { i -> backend.analyzeFrame(frame(i.toLong(), i * 33L)) }
        val firstSummary = backend.endSession() as SquatActivitySessionSummary
        assertEquals(1, firstSummary.repRecords.size)

        writer = RecordingTelemetryWriter()
        backend.startSession(session(id = "s2"))
        val result = backend.analyzeFrame(frame(0, 0)) as SquatActivityFrameResult

        assertEquals(SquatPhase.STANDING, result.phase)
        assertEquals(0, result.repCount)
        assertEquals(listOf(0L), writer.enqueued.map { it.frameIndex })
    }

    // ---- End drains the writer before atomically storing the summary ----

    @Test
    fun `endSession drains the telemetry writer before atomically storing the summary`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val estimator = ScriptedPoseEstimator(listOf(landmarksForKneeAngle(170.0)))
        val writer = RecordingTelemetryWriter(events = events)
        val summaryStore = tempSummaryStore(onBeforeRename = { _, _ -> events.add("summary_write") })
        val backend = newBackend(estimator, writer = writer, summaryStore = summaryStore)
        backend.initialize()
        backend.startSession(session())
        backend.analyzeFrame(frame(0, 0))

        backend.endSession()

        assertEquals(listOf("writer_finish", "summary_write"), events)
    }

    @Test
    fun `endSession returns a summary with every completed rep record`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(oneRepAngles.map { landmarksForKneeAngle(it) })
        val backend = newBackend(estimator)
        backend.initialize()
        backend.startSession(session(id = "rep-session"))
        oneRepAngles.indices.forEach { i -> backend.analyzeFrame(frame(i.toLong(), i * 33L)) }

        val summary = backend.endSession() as SquatActivitySessionSummary

        assertEquals(SessionCompletion.COMPLETE, summary.completion)
        assertEquals(1, summary.repRecords.size)
        assertEquals(1, summary.repRecords.single().repNumber)
        assertEquals("rep-session", summary.sessionId)
    }

    @Test
    fun `endSession persists selected squat view into summary details`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(listOf(landmarksForKneeAngle(170.0)))
        val summaryStore = tempSummaryStore()
        val backend = newBackend(
            estimator = estimator,
            summaryStore = summaryStore,
            view = SquatView.BACK,
        )
        backend.initialize()
        backend.startSession(session(id = "selected-view"))
        backend.analyzeFrame(frame(0, 0))

        backend.endSession()

        val details = summaryStore.read("selected-view")!!.details as SquatSessionDetails
        assertEquals("back", details.view)
    }

    @Test
    fun `frame result exposes immediate alerts and selected view`(): Unit = runBlocking {
        val frontValgusLandmarks = mapOf(
            "l_knee" to Landmark(130.0, 200.0, 0.99),
            "l_ankle" to Landmark(100.0, 300.0, 0.99),
        )
        val estimator = ScriptedPoseEstimator(listOf(frontValgusLandmarks))
        val backend = newBackend(estimator, view = SquatView.FRONT)
        backend.initialize()
        backend.startSession(session(id = "alert-result"))

        val result = backend.analyzeFrame(frame(0, 0)) as SquatActivityFrameResult

        assertEquals(SquatView.FRONT, result.view)
        assertEquals("valgus_l", result.immediateAlerts.single().metric)
    }

    @Test
    fun `endSession persists annotated video file name when provider returns one`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(listOf(landmarksForKneeAngle(170.0)))
        val summaryStore = tempSummaryStore()
        val backend = SquatBackend(
            view = SquatView.FRONT,
            poseEstimatorFactory = { estimator },
            telemetryWriterFactory = { RecordingTelemetryWriter() },
            summaryStore = summaryStore,
            annotatedVideoFileNameProvider = { session -> "${session.id}.mp4" },
        )
        backend.initialize()
        backend.startSession(session(id = "video-summary"))
        backend.analyzeFrame(frame(0, 0))

        val summary = backend.endSession() as SquatActivitySessionSummary

        assertEquals("video-summary.mp4", summary.annotatedVideoFileName)
        val details = summaryStore.read("video-summary")!!.details as SquatSessionDetails
        assertEquals("video-summary.mp4", details.annotatedVideoFileName)
    }

    @Test
    fun `startSession samples selected view and keeps it stable for that session`(): Unit = runBlocking {
        var selectedView = SquatView.FRONT
        val estimator = ScriptedPoseEstimator(
            listOf(landmarksForKneeAngle(170.0), landmarksForKneeAngle(155.0)),
        )
        val writer = RecordingTelemetryWriter()
        val summaryStore = tempSummaryStore()
        val backend = SquatBackend(
            view = SquatView.SIDE,
            poseEstimatorFactory = { estimator },
            telemetryWriterFactory = { writer },
            summaryStore = summaryStore,
            sessionViewProvider = { selectedView },
        )
        backend.initialize()
        selectedView = SquatView.BACK
        backend.startSession(session(id = "stable-view"))
        selectedView = SquatView.FRONT

        backend.analyzeFrame(frame(0, 0))
        backend.analyzeFrame(frame(1, 33))
        backend.endSession()

        assertEquals(
            listOf("back", "back"),
            writer.enqueued.map { (it.payload as SquatTelemetryPayload).view },
        )
        val details = summaryStore.read("stable-view")!!.details as SquatSessionDetails
        assertEquals("back", details.view)
    }

    @Test
    fun `endSession without a started session throws`(): Unit = runBlocking {
        val backend = newBackend(ScriptedPoseEstimator(listOf()))
        backend.initialize()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { backend.endSession() }
        }
    }

    @Test
    fun `writer factory failure leaves no active session`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(listOf(landmarksForKneeAngle(170.0)))
        val backend = SquatBackend(
            view = SquatView.FRONT,
            poseEstimatorFactory = { estimator },
            telemetryWriterFactory = { throw IllegalStateException("storage unavailable") },
            summaryStore = tempSummaryStore(),
        )
        backend.initialize()

        val failure = assertThrows(IllegalStateException::class.java) {
            backend.startSession(session())
        }

        assertEquals("storage unavailable", failure.message)
        val preview = backend.analyzeFrame(frame(0, 0)) as SquatActivityFrameResult
        assertEquals(0, preview.repCount)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { backend.endSession() }
        }
    }

    @Test
    fun `endSession waits for an active frame and includes it before draining`(): Unit = runBlocking {
        val enteredInference = CountDownLatch(1)
        val releaseInference = CountDownLatch(1)
        val estimator = object : SquatPoseEstimator {
            override fun run(bitmap: Bitmap): Landmarks {
                enteredInference.countDown()
                check(releaseInference.await(5, TimeUnit.SECONDS))
                return landmarksForKneeAngle(170.0)
            }

            override fun close() = Unit
        }
        val writer = RecordingTelemetryWriter()
        val backend = SquatBackend(
            view = SquatView.FRONT,
            poseEstimatorFactory = { estimator },
            telemetryWriterFactory = { writer },
            summaryStore = tempSummaryStore(),
        )
        val executor = Executors.newSingleThreadExecutor()
        backend.initialize()
        backend.startSession(session())

        val frameFuture = executor.submit<ActivityFrameResult> {
            backend.analyzeFrame(frame(0, 0))
        }
        assertTrue(enteredInference.await(5, TimeUnit.SECONDS))

        val ending = async { backend.endSession() as SquatActivitySessionSummary }
        yield()
        assertFalse(ending.isCompleted)
        releaseInference.countDown()
        frameFuture.get(5, TimeUnit.SECONDS) as SquatActivityFrameResult
        val summary = ending.await()
        executor.shutdownNow()

        assertEquals(SessionCompletion.COMPLETE, summary.completion)
        assertEquals(1, writer.enqueued.size)
        assertEquals(1L, summary.frameCount)
    }

    @Test
    fun `close aborts session but defers estimator close until inference exits`(): Unit = runBlocking {
        val enteredInference = CountDownLatch(1)
        val releaseInference = CountDownLatch(1)
        var estimatorCloseCount = 0
        val estimator = object : SquatPoseEstimator {
            override fun run(bitmap: Bitmap): Landmarks {
                enteredInference.countDown()
                check(releaseInference.await(5, TimeUnit.SECONDS))
                return landmarksForKneeAngle(170.0)
            }

            override fun close() {
                estimatorCloseCount++
            }
        }
        val writer = RecordingTelemetryWriter()
        val backend = SquatBackend(
            view = SquatView.FRONT,
            poseEstimatorFactory = { estimator },
            telemetryWriterFactory = { writer },
            summaryStore = tempSummaryStore(),
        )
        val executor = Executors.newSingleThreadExecutor()
        backend.initialize()
        backend.startSession(session())
        val frameFuture = executor.submit {
            backend.analyzeFrame(frame(0, 0))
        }
        assertTrue(enteredInference.await(5, TimeUnit.SECONDS))

        backend.close()

        assertEquals(1, writer.abortCalls)
        assertEquals(0, estimatorCloseCount)
        releaseInference.countDown()
        assertThrows(Exception::class.java) { frameFuture.get(5, TimeUnit.SECONDS) }
        assertEquals(1, estimatorCloseCount)
        executor.shutdownNow()
    }

    // ---- telemetry failure surfaces immediately and marks the session incomplete ----

    @Test
    fun `enqueue failure surfaces immediately from analyzeFrame and marks the session incomplete`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(
            listOf(landmarksForKneeAngle(170.0), landmarksForKneeAngle(150.0), landmarksForKneeAngle(120.0)),
        )
        val writer = RecordingTelemetryWriter(failAtAttempt = 2)
        val backend = newBackend(estimator, writer = writer)
        backend.initialize()
        backend.startSession(session())

        backend.analyzeFrame(frame(0, 0))
        val failure = assertThrows(IllegalStateException::class.java) {
            backend.analyzeFrame(frame(1, 33))
        }
        assertTrue(failure.message.orEmpty().contains("simulated telemetry queue failure"))

        // Fail-fast: a further call throws immediately without running inference again.
        assertThrows(IllegalStateException::class.java) {
            backend.analyzeFrame(frame(2, 66))
        }
        assertEquals(2, estimator.runCount)

        val summary = backend.endSession() as SquatActivitySessionSummary
        assertEquals(SessionCompletion.INCOMPLETE, summary.completion)
        assertEquals(1, writer.failCalls)
        assertEquals(0, writer.finishCalls)
    }

    // ---- close ends/cancels resources exactly once ----

    @Test
    fun `close closes the pose estimator exactly once even when called twice`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(listOf())
        val backend = newBackend(estimator)
        backend.initialize()

        backend.close()
        backend.close()

        assertEquals(1, estimator.closeCount)
    }

    @Test
    fun `close before initialize does not throw and closes nothing`() {
        val estimator = ScriptedPoseEstimator(listOf())
        val backend = newBackend(estimator)

        backend.close()

        assertEquals(0, estimator.closeCount)
    }

    @Test
    fun `close aborts an abandoned session's writer exactly once without blocking`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(listOf(landmarksForKneeAngle(170.0)))
        val writer = RecordingTelemetryWriter()
        val backend = newBackend(estimator, writer = writer)
        backend.initialize()
        backend.startSession(session())
        backend.analyzeFrame(frame(0, 0))

        backend.close()
        backend.close()

        assertEquals(1, writer.abortCalls)
        assertNotNull(writer.failedCause)
        assertEquals(0, writer.finishCalls)
        assertEquals(1, estimator.closeCount)
    }

    @Test
    fun `close after a clean endSession does not re-finalize the writer`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(listOf(landmarksForKneeAngle(170.0)))
        val writer = RecordingTelemetryWriter()
        val backend = newBackend(estimator, writer = writer)
        backend.initialize()
        backend.startSession(session())
        backend.analyzeFrame(frame(0, 0))
        backend.endSession()

        backend.close()

        assertEquals(1, writer.finishCalls)
        assertEquals(0, writer.failCalls)
        assertEquals(0, writer.abortCalls)
    }

    // ---- real writer/codec/summary-store round trip ----

    @Test
    fun `real JsonlTelemetryWriter and SessionSummaryStore round-trip a live session`(): Unit = runBlocking {
        val estimator = ScriptedPoseEstimator(oneRepAngles.map { landmarksForKneeAngle(it) })
        val telemetryDir = Files.createTempDirectory("squat-backend-telemetry").also { createdDirectories.add(it) }
        val summaryStore = tempSummaryStore()
        val backend = SquatBackend(
            view = SquatView.FRONT,
            poseEstimatorFactory = { estimator },
            telemetryWriterFactory = { s ->
                JsonlTelemetryWriter(outputDirectory = telemetryDir, fileName = "${s.id}.jsonl")
            },
            summaryStore = summaryStore,
        )
        backend.initialize()
        backend.startSession(session(id = "e2e"))
        oneRepAngles.indices.forEach { i -> backend.analyzeFrame(frame(i.toLong(), i * 33L)) }

        val summary = backend.endSession() as SquatActivitySessionSummary

        val codec = TelemetryCodec()
        val lines = Files.readAllLines(telemetryDir.resolve("e2e.jsonl"))
        assertEquals(oneRepAngles.size, lines.size)
        val decoded = lines.map { codec.decodeEnvelope(it) }
        assertEquals((0 until oneRepAngles.size).map { it.toLong() }, decoded.map { it.frameIndex })
        assertTrue(decoded.all { it.payload is SquatTelemetryPayload })

        val persisted = summaryStore.read("e2e")
        assertNotNull(persisted)
        assertEquals(SessionCompletion.COMPLETE, persisted!!.completion)
        assertEquals("e2e.jsonl", persisted.telemetryFileName)
        assertEquals(1.0, persisted.metrics["rep_count"])
        assertEquals(summary.repRecords.size.toDouble(), persisted.metrics["rep_count"])
        val details = persisted.details as SquatSessionDetails
        val persistedRep = details.reps.single()
        val runtimeRep = summary.repRecords.single()
        assertEquals(runtimeRep.repNumber, persistedRep.repNumber)
        assertEquals(runtimeRep.grade.name, persistedRep.grade)
        assertEquals(runtimeRep.minKneeAngleDeg, persistedRep.minKneeAngleDeg, 0.0)
        assertEquals(runtimeRep.score, persistedRep.score)
        assertEquals(runtimeRep.confidenceCoverage, persistedRep.confidenceCoverage, 0.0)
        assertEquals(runtimeRep.primaryCorrection, persistedRep.primaryCorrection)
        assertEquals(runtimeRep.secondaryCorrections, persistedRep.secondaryCorrections)
        assertEquals(
            runtimeRep.issues.mapValues { it.value.wireValue },
            persistedRep.issueSeverities,
        )
        assertEquals(runtimeRep.issueMessages, persistedRep.issueMessages)
        assertEquals(runtimeRep.metricSummaries.keys, persistedRep.metricSummaries.keys)
        assertNull(persistedRep.metricSummaries.getValue("heel_lift").mean)
        assertEquals("front", details.view)
        assertTrue(
            decoded.all { envelope ->
                (envelope.payload as SquatTelemetryPayload).view == "front"
            },
        )
        assertEquals(
            "NOT_APPLICABLE",
            persistedRep.metricSummaries.getValue("heel_lift").status,
        )
    }
}
