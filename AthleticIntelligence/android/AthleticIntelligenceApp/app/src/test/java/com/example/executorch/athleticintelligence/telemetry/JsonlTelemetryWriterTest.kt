/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.telemetry

import com.example.executorch.athleticintelligence.activity.ActivityType
import com.example.executorch.athleticintelligence.activity.SessionCompletion
import java.io.IOException
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonlTelemetryWriterTest {
    private val codec = TelemetryCodec()

    @Test
    fun `writes one jsonl line per accepted frame in frame order`() = runTest {
        val outputDir = Files.createTempDirectory("jsonl-writer-order")
        val writer = JsonlTelemetryWriter(
            outputDirectory = outputDir,
            fileName = "session.jsonl",
            queueCapacity = 8,
        )

        assertTrue(writer.enqueue(envelope(frameIndex = 1L)).isSuccess)
        assertTrue(writer.enqueue(envelope(frameIndex = 2L)).isSuccess)
        assertTrue(writer.enqueue(envelope(frameIndex = 3L)).isSuccess)

        val artifact = writer.finish()
        val lines = Files.readAllLines(outputDir.resolve("session.jsonl"), StandardCharsets.UTF_8)

        assertEquals(SessionCompletion.COMPLETE, artifact.completion)
        assertEquals(3L, artifact.frameCount)
        assertEquals(3, lines.size)
        assertEquals(
            listOf(1L, 2L, 3L),
            lines.map { codec.decodeEnvelope(it).frameIndex },
        )

        deleteRecursively(outputDir)
    }

    @Test
    fun `rejects non-increasing frame indices and marks artifact incomplete`() = runTest {
        val outputDir = Files.createTempDirectory("jsonl-writer-monotonic")
        val writer = JsonlTelemetryWriter(
            outputDirectory = outputDir,
            fileName = "session.jsonl",
            queueCapacity = 8,
        )

        assertTrue(writer.enqueue(envelope(frameIndex = 5L)).isSuccess)
        val repeated = writer.enqueue(envelope(frameIndex = 5L))

        assertTrue(repeated.isFailure)
        assertTrue(repeated.exceptionOrNull()!!.message!!.contains("frame index"))

        val artifact = writer.finish()
        assertEquals(SessionCompletion.INCOMPLETE, artifact.completion)

        deleteRecursively(outputDir)
    }

    @Test
    fun `drops frames without failing the session when queue is saturated`() = runTest {
        val outputDir = Files.createTempDirectory("jsonl-writer-capacity")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val writer = JsonlTelemetryWriter(
            outputDirectory = outputDir,
            fileName = "session.jsonl",
            queueCapacity = 1,
            ioDispatcher = dispatcher,
        )

        // Writer coroutine is paused (test dispatcher), so the 1-slot buffer fills after
        // the first frame; the rest saturate.
        val first = writer.enqueue(envelope(frameIndex = 1L))
        val second = writer.enqueue(envelope(frameIndex = 2L))
        val third = writer.enqueue(envelope(frameIndex = 3L))

        // Telemetry is best-effort: saturated frames are dropped, never fatal.
        assertTrue(first.isSuccess)
        assertTrue(second.isSuccess)
        assertTrue(third.isSuccess)

        advanceUntilIdle()
        val artifact = writer.finish()
        val lines = Files.readAllLines(outputDir.resolve("session.jsonl"), StandardCharsets.UTF_8)

        // Session still completes; only the accepted frame is written; drops are reported.
        assertEquals(SessionCompletion.COMPLETE, artifact.completion)
        assertEquals(1L, artifact.frameCount)
        assertEquals(1, lines.size)
        assertTrue(artifact.errorMessage!!.contains("Dropped 2"))

        deleteRecursively(outputDir)
    }

    @Test
    fun `finish drains all accepted records before returning`() = runTest {
        val outputDir = Files.createTempDirectory("jsonl-writer-drain")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val writer = JsonlTelemetryWriter(
            outputDirectory = outputDir,
            fileName = "session.jsonl",
            queueCapacity = 16,
            ioDispatcher = dispatcher,
        )

        for (index in 1L..10L) {
            assertTrue(writer.enqueue(envelope(frameIndex = index)).isSuccess)
        }

        advanceUntilIdle()
        val artifact = writer.finish()
        val lines = Files.readAllLines(outputDir.resolve("session.jsonl"), StandardCharsets.UTF_8)

        assertEquals(SessionCompletion.COMPLETE, artifact.completion)
        assertEquals(10L, artifact.frameCount)
        assertEquals(10, lines.size)

        deleteRecursively(outputDir)
    }

    @Test
    fun `finish cannot snapshot state while an accepted enqueue is being recorded`() {
        val outputDir = Files.createTempDirectory("jsonl-writer-concurrency")
        val acceptedSendPaused = CountDownLatch(1)
        val releaseAcceptedSend = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val writer = JsonlTelemetryWriter(
            outputDirectory = outputDir,
            fileName = "session.jsonl",
            queueCapacity = 8,
            onEnvelopeAccepted = {
                acceptedSendPaused.countDown()
                assertTrue(releaseAcceptedSend.await(5, TimeUnit.SECONDS))
            },
        )

        try {
            val enqueueFuture = executor.submit<Result<Unit>> {
                writer.enqueue(envelope(frameIndex = 1L))
            }
            assertTrue(acceptedSendPaused.await(5, TimeUnit.SECONDS))

            val finishFuture = executor.submit<TelemetryArtifact> {
                runBlocking { writer.finish() }
            }
            assertThrows(TimeoutException::class.java) {
                finishFuture.get(100, TimeUnit.MILLISECONDS)
            }

            releaseAcceptedSend.countDown()
            assertTrue(enqueueFuture.get(5, TimeUnit.SECONDS).isSuccess)
            val artifact = finishFuture.get(5, TimeUnit.SECONDS)
            val lines = Files.readAllLines(outputDir.resolve("session.jsonl"), StandardCharsets.UTF_8)

            assertEquals(1L, artifact.frameCount)
            assertEquals(1, lines.size)
        } finally {
            releaseAcceptedSend.countDown()
            executor.shutdownNow()
            deleteRecursively(outputDir)
        }
    }

    @Test
    fun `fail after complete finish returns cached artifact without hidden mutation`() = runTest {
        val outputDir = Files.createTempDirectory("jsonl-writer-terminal")
        val writer = JsonlTelemetryWriter(
            outputDirectory = outputDir,
            fileName = "session.jsonl",
            queueCapacity = 8,
        )
        assertTrue(writer.enqueue(envelope(frameIndex = 1L)).isSuccess)

        val completed = writer.finish()
        val afterLateFailure = writer.fail(IllegalStateException("late failure"))
        val rejectedEnqueue = writer.enqueue(envelope(frameIndex = 2L))

        assertEquals(SessionCompletion.COMPLETE, completed.completion)
        assertSame(completed, afterLateFailure)
        assertTrue(rejectedEnqueue.isFailure)
        assertTrue(rejectedEnqueue.exceptionOrNull()!!.message!!.contains("finalized"))

        deleteRecursively(outputDir)
    }

    @Test
    fun `write failures produce incomplete artifacts`() = runTest {
        val outputDir = Files.createTempDirectory("jsonl-writer-failure")
        val writer = JsonlTelemetryWriter(
            outputDirectory = outputDir,
            fileName = "session.jsonl",
            queueCapacity = 8,
            writerFactory = { FailingWriter() },
        )

        assertTrue(writer.enqueue(envelope(frameIndex = 1L)).isSuccess)

        val artifact = writer.finish()

        assertEquals(SessionCompletion.INCOMPLETE, artifact.completion)
        assertTrue(artifact.errorMessage!!.contains("disk full"))

        deleteRecursively(outputDir)
    }

    @Test
    fun `abort marks incomplete without blocking on writer IO`() {
        val outputDir = Files.createTempDirectory("jsonl-writer-abort")
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val writer = JsonlTelemetryWriter(
            outputDirectory = outputDir,
            fileName = "session.jsonl",
            queueCapacity = 8,
            writerFactory = { BlockingWriter(writeStarted, releaseWrite) },
        )
        assertTrue(writer.enqueue(envelope(frameIndex = 1L)).isSuccess)
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS))

        val executor = Executors.newSingleThreadExecutor()
        val abortFuture = executor.submit {
            writer.abort(IllegalStateException("abandoned session"))
        }

        abortFuture.get(1, TimeUnit.SECONDS)
        releaseWrite.countDown()
        val artifact = runBlocking {
            writer.fail(IllegalStateException("abandoned session"))
        }

        assertEquals(SessionCompletion.INCOMPLETE, artifact.completion)
        assertTrue(artifact.errorMessage!!.contains("abandoned session"))
        executor.shutdownNow()
        deleteRecursively(outputDir)
    }

    private fun envelope(frameIndex: Long): TelemetryEnvelope =
        TelemetryEnvelope(
            timestampUtc = "2026-07-12T23:00:00Z",
            sessionElapsedMs = frameIndex * 10L,
            frameIndex = frameIndex,
            source = TelemetrySource.LIVE_CAMERA,
            activityType = ActivityType.SQUATS,
            payload = SquatTelemetryPayload(phase = "standing", repCount = 0),
        )

    private fun deleteRecursively(path: Path) {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }

    private class FailingWriter : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) {
            throw IOException("disk full")
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    private class BlockingWriter(
        private val writeStarted: CountDownLatch,
        private val releaseWrite: CountDownLatch,
    ) : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) {
            writeStarted.countDown()
            check(releaseWrite.await(5, TimeUnit.SECONDS))
        }

        override fun flush() = Unit

        override fun close() = Unit
    }
}
