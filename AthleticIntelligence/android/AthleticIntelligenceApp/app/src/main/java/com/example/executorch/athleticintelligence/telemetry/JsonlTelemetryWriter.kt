/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.telemetry

import com.example.executorch.athleticintelligence.activity.SessionCompletion
import java.io.Writer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class JsonlTelemetryWriter(
    private val outputDirectory: Path,
    private val fileName: String,
    private val queueCapacity: Int = 256,
    private val codec: TelemetryCodec = TelemetryCodec(),
    ioDispatcher: CoroutineDispatcher? = null,
    private val writerFactory: (Path) -> Writer = { outputPath ->
        Files.newBufferedWriter(
            outputPath,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    },
    private val onEnvelopeAccepted: (() -> Unit)? = null,
) : SessionTelemetryWriter {
    private val ownedDispatcher: CoroutineDispatcher? =
        if (ioDispatcher == null) {
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "athletic-intelligence-telemetry-writer")
            }.asCoroutineDispatcher()
        } else {
            null
        }
    private val dispatcher: CoroutineDispatcher = ioDispatcher ?: ownedDispatcher ?: Dispatchers.IO
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val channel = Channel<TelemetryEnvelope>(capacity = queueCapacity)
    private val stateLock = Any()
    private val finalizeMutex = Mutex()
    private var closed = false
    private var acceptedFrameCount = 0L
    private var droppedFrameCount = 0L
    private var lastAcceptedFrameIndex = Long.MIN_VALUE
    private var terminalFailure: Throwable? = null
    private var finalizedArtifact: TelemetryArtifact? = null
    private val outputFilePath: Path = outputDirectory.resolve(fileName)

    private val writerJob = scope.launch {
        try {
            Files.createDirectories(outputDirectory)
            writerFactory(outputFilePath).use { writer ->
                for (envelope in channel) {
                    writer.write(codec.encodeEnvelope(envelope))
                    writer.write("\n")
                }
                writer.flush()
            }
        } catch (failure: Throwable) {
            markFailureIfAbsent(failure)
        }
    }

    override fun enqueue(envelope: TelemetryEnvelope): Result<Unit> =
        synchronized(stateLock) {
            terminalFailure?.let { return Result.failure(it) }
            if (closed) {
                return Result.failure(IllegalStateException("Telemetry writer already finalized"))
            }

            val previousFrame = lastAcceptedFrameIndex
            if (envelope.frameIndex <= previousFrame) {
                val failure = IllegalArgumentException(
                    "Telemetry frame index must increase: previous=$previousFrame new=${envelope.frameIndex}",
                )
                markFailureIfAbsent(failure)
                return Result.failure(failure)
            }

            val sendResult = channel.trySend(envelope)
            if (sendResult.isSuccess) {
                onEnvelopeAccepted?.invoke()
                lastAcceptedFrameIndex = envelope.frameIndex
                acceptedFrameCount += 1L
                return Result.success(Unit)
            }

            if (sendResult.isClosed) {
                val failure = IllegalStateException("Telemetry queue is closed")
                markFailureIfAbsent(failure)
                return Result.failure(failure)
            }

            // Queue saturation is expected when frame production briefly outruns the JSONL
            // writer. Telemetry is best-effort diagnostic data, so drop this frame rather
            // than failing the session. The artifact reports the drop count.
            droppedFrameCount += 1L
            return Result.success(Unit)
        }

    override suspend fun finish(): TelemetryArtifact = finalizeArtifact(failure = null)

    override suspend fun fail(cause: Throwable): TelemetryArtifact = finalizeArtifact(failure = cause)

    /**
     * Non-blocking emergency shutdown for an owner that cannot suspend (for
     * example, an unexpected backend [AutoCloseable.close] with an abandoned
     * active session). Marks the artifact incomplete and rejects new frames
     * synchronously, then lets the writer coroutine drain accepted frames and
     * close its file/owned dispatcher asynchronously.
     */
    override fun abort(cause: Throwable) {
        synchronized(stateLock) {
            if (finalizedArtifact != null || closed) return
            if (terminalFailure == null) terminalFailure = cause
            closed = true
            channel.close()
        }
        scope.launch { finalizeArtifact(cause) }
    }

    private suspend fun finalizeArtifact(failure: Throwable?): TelemetryArtifact =
        finalizeMutex.withLock {
            synchronized(stateLock) {
                finalizedArtifact?.let { return@withLock it }
                if (failure != null && terminalFailure == null) {
                    terminalFailure = failure
                }
                if (!closed) {
                    closed = true
                    channel.close()
                }
            }

            writerJob.join()
            ownedDispatcher?.let { owned ->
                (owned as? AutoCloseable)?.close()
            }

            synchronized(stateLock) {
                finalizedArtifact ?: TelemetryArtifact(
                    fileName = fileName,
                    frameCount = acceptedFrameCount,
                    completion =
                        if (terminalFailure == null) {
                            SessionCompletion.COMPLETE
                        } else {
                            SessionCompletion.INCOMPLETE
                        },
                    errorMessage = terminalFailure?.message
                        ?: droppedFrameCount.takeIf { it > 0L }?.let {
                            "Dropped $it telemetry frame(s) under backpressure"
                        },
                ).also { finalizedArtifact = it }
            }
        }

    private fun markFailureIfAbsent(failure: Throwable) {
        synchronized(stateLock) {
            if (finalizedArtifact == null && terminalFailure == null) {
                terminalFailure = failure
            }
        }
    }
}
