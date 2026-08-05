/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.example.executorch.athleticintelligence.squat.SquatAnnotationFrame
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ArrayBlockingQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AnnotatedVideoArtifact(
    val fileName: String,
    val file: File,
    val frameCount: Long,
)

/** Converts session elapsed timestamps to monotonic video presentation times. */
internal class VideoPresentationTimeline {
    private var firstTimestampUs: Long? = null
    private var lastTimestampUs = -1L

    fun normalize(sessionTimestampUs: Long): Long {
        val first = firstTimestampUs ?: sessionTimestampUs.also { firstTimestampUs = it }
        val relative = (sessionTimestampUs - first).coerceAtLeast(0L)
        return relative.coerceAtLeast(lastTimestampUs + 1L).also { lastTimestampUs = it }
    }
}

class AnnotatedVideoRecorder(
    private val outputDirectory: Path,
    private val renderer: SquatAnnotationCanvasRenderer = SquatAnnotationCanvasRenderer(),
) {
    private val stateLock = Object()
    private var state = RecorderState.IDLE
    private var queue: ArrayBlockingQueue<RecorderCommand>? = null
    private var worker: Thread? = null
    private var outputFile: File? = null
    private var completion: Result<AnnotatedVideoArtifact?> = Result.success(null)

    fun start(sessionId: String): Result<Unit> = runCatching {
        synchronized(stateLock) {
            check(state == RecorderState.IDLE) { "Annotated video recorder is already active" }
            Files.createDirectories(outputDirectory)
            val fileName = "${safeFileStem(sessionId)}.mp4"
            val file = outputDirectory.resolve(fileName).toFile()
            if (file.exists()) file.delete()
            val frameQueue = ArrayBlockingQueue<RecorderCommand>(3)
            queue = frameQueue
            outputFile = file
            completion = Result.success(null)
            state = RecorderState.RECORDING
            worker = Thread(
                { runWorker(frameQueue, fileName, file) },
                "squat-annotated-video-recorder",
            ).also { it.start() }
        }
    }

    fun submit(
        bitmap: Bitmap,
        annotation: SquatAnnotationFrame,
        presentationTimeUs: Long,
    ): Boolean {
        val frameQueue = synchronized(stateLock) {
            if (state != RecorderState.RECORDING) return false
            queue
        } ?: return false

        val command = RecorderCommand.Frame(bitmap, annotation, presentationTimeUs)
        if (frameQueue.offer(command)) return true
        frameQueue.poll()
        return frameQueue.offer(command)
    }

    suspend fun stop(): Result<AnnotatedVideoArtifact?> = withContext(Dispatchers.IO) {
        val snapshot = synchronized(stateLock) {
            if (state == RecorderState.IDLE) return@withContext Result.success(null)
            if (state == RecorderState.STOPPING) {
                return@withContext completion
            }
            state = RecorderState.STOPPING
            WorkerSnapshot(queue, worker)
        }
        snapshot.queue?.put(RecorderCommand.Stop)
        snapshot.worker?.join()
        synchronized(stateLock) { completion }
    }

    fun abort() {
        val snapshot = synchronized(stateLock) {
            if (state == RecorderState.IDLE) return
            state = RecorderState.STOPPING
            WorkerSnapshot(queue, worker)
        }
        snapshot.queue?.clear()
        snapshot.queue?.offer(RecorderCommand.Stop)
        snapshot.worker?.join(1_000L)
        synchronized(stateLock) {
            outputFile?.delete()
            clearStateLocked(Result.success(null))
        }
    }

    private fun runWorker(
        frameQueue: ArrayBlockingQueue<RecorderCommand>,
        fileName: String,
        file: File,
    ) {
        var encoder: EncoderSession? = null
        var frameCount = 0L
        val result = runCatching {
            while (true) {
                when (val command = frameQueue.take()) {
                    is RecorderCommand.Frame -> {
                        val activeEncoder = encoder ?: EncoderSession(
                            file = file,
                            width = evenDimension(command.bitmap.width),
                            height = evenDimension(command.bitmap.height),
                            renderer = renderer,
                        ).also { encoder = it }
                        activeEncoder.writeFrame(command)
                        frameCount++
                    }
                    RecorderCommand.Stop -> break
                }
            }
            encoder?.finish()
            if (frameCount == 0L || !file.exists() || file.length() == 0L) {
                file.delete()
                null
            } else {
                AnnotatedVideoArtifact(fileName, file, frameCount)
            }
        }.onFailure {
            runCatching { encoder?.release() }
            file.delete()
        }

        synchronized(stateLock) {
            clearStateLocked(result)
        }
    }

    private fun clearStateLocked(result: Result<AnnotatedVideoArtifact?>) {
        queue = null
        worker = null
        outputFile = null
        completion = result
        state = RecorderState.IDLE
    }

    private fun safeFileStem(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "squat-session" }

    private fun evenDimension(value: Int): Int =
        value.coerceAtLeast(2).let { if (it % 2 == 0) it else it - 1 }

    private enum class RecorderState {
        IDLE,
        RECORDING,
        STOPPING,
    }

    private data class WorkerSnapshot(
        val queue: ArrayBlockingQueue<RecorderCommand>?,
        val worker: Thread?,
    )

    private sealed interface RecorderCommand {
        data class Frame(
            val bitmap: Bitmap,
            val annotation: SquatAnnotationFrame,
            val presentationTimeUs: Long,
        ) : RecorderCommand

        data object Stop : RecorderCommand
    }

    private class EncoderSession(
        private val file: File,
        private val width: Int,
        private val height: Int,
        private val renderer: SquatAnnotationCanvasRenderer,
    ) {
        private val bufferInfo = MediaCodec.BufferInfo()
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val presentationTimeline = VideoPresentationTimeline()
        private val pendingPresentationTimesUs = ArrayDeque<Long>()
        private val codec: MediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        private val muxer: MediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        private val inputSurface: Surface
        private var muxerStarted = false
        private var trackIndex = -1
        private var released = false

        init {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            codec.start()
        }

        fun writeFrame(command: RecorderCommand.Frame) {
            val canvas = inputSurface.lockCanvas(null)
            try {
                drawFrame(canvas, command)
            } finally {
                pendingPresentationTimesUs.addLast(
                    presentationTimeline.normalize(command.presentationTimeUs),
                )
                inputSurface.unlockCanvasAndPost(canvas)
            }
            drain(endOfStream = false)
        }

        fun finish() {
            codec.signalEndOfInputStream()
            drain(endOfStream = true)
            release()
        }

        fun release() {
            if (released) return
            released = true
            runCatching { inputSurface.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }

        private fun drawFrame(canvas: Canvas, command: RecorderCommand.Frame) {
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(command.bitmap, null, Rect(0, 0, width, height), bitmapPaint)
            renderer.draw(canvas, command.annotation, width, height)
        }

        private fun drain(endOfStream: Boolean) {
            while (true) {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
                when {
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(!muxerStarted) { "Encoder output format changed after muxer started" }
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    outputBufferIndex >= 0 -> {
                        val encodedData = codec.getOutputBuffer(outputBufferIndex)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            bufferInfo.size = 0
                        }
                        if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            pendingPresentationTimesUs.removeFirstOrNull()?.let { timestampUs ->
                                bufferInfo.presentationTimeUs = timestampUs
                            }
                            muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
    }
}
