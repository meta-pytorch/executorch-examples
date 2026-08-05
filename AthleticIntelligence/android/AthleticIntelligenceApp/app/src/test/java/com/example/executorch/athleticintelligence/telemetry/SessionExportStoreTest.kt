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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionExportStoreTest {
    @Test
    fun `exports persisted summary and telemetry as a zip bundle`() {
        val root = Files.createTempDirectory("session-export-store")
        val summaryDir = root.resolve("session-summaries")
        val telemetryDir = root.resolve("telemetry")
        val exportDir = root.resolve("session-exports")
        val videoDir = root.resolve("squat-videos")
        Files.createDirectories(summaryDir)
        Files.createDirectories(telemetryDir)
        Files.createDirectories(videoDir)
        val summaryJson = TelemetryCodec().encodeSummary(
            summary(
                sessionId = "squat-1",
                telemetryFileName = "squat-1.jsonl",
                annotatedVideoFileName = "squat-1.mp4",
            ),
        )
        val framesJsonl = """{"frame_index":0}""" + "\n" + """{"frame_index":1}""" + "\n"
        val videoBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        Files.write(summaryDir.resolve("squat-1.json"), summaryJson.toByteArray(StandardCharsets.UTF_8))
        Files.write(telemetryDir.resolve("squat-1.jsonl"), framesJsonl.toByteArray(StandardCharsets.UTF_8))
        Files.write(videoDir.resolve("squat-1.mp4"), videoBytes)
        val store = SessionExportStore(summaryDir, telemetryDir, exportDir, videoDir)

        val artifact = store.exportSession("squat-1")

        assertEquals("application/zip", artifact.mimeType)
        assertEquals("squat-1.zip", artifact.displayName)
        assertTrue(artifact.file.exists())
        ZipFile(artifact.file).use { zip ->
            assertEquals(summaryJson, zip.readText("summary.json"))
            assertEquals(framesJsonl, zip.readText("frames.jsonl"))
            assertEquals(videoBytes.toList(), zip.readBytes("annotated.mp4").toList())
        }
        deleteRecursively(root)
    }

    @Test
    fun `missing summary fails with a clear error`() {
        val root = Files.createTempDirectory("session-export-store-missing-summary")
        val store = SessionExportStore(
            root.resolve("session-summaries"),
            root.resolve("telemetry"),
            root.resolve("session-exports"),
            root.resolve("squat-videos"),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            store.exportSession("missing-session")
        }

        assertEquals("Session summary missing for missing-session", failure.message)
        deleteRecursively(root)
    }

    @Test
    fun `missing telemetry fails with a clear error`() {
        val root = Files.createTempDirectory("session-export-store-missing-telemetry")
        val summaryDir = root.resolve("session-summaries")
        Files.createDirectories(summaryDir)
        val summaryJson = TelemetryCodec().encodeSummary(summary("squat-2", "missing.jsonl"))
        Files.write(summaryDir.resolve("squat-2.json"), summaryJson.toByteArray(StandardCharsets.UTF_8))
        val store = SessionExportStore(
            summaryDir,
            root.resolve("telemetry"),
            root.resolve("session-exports"),
            root.resolve("squat-videos"),
        )

        val failure = assertThrows(IllegalStateException::class.java) {
            store.exportSession("squat-2")
        }

        assertEquals("Session telemetry missing for squat-2", failure.message)
        deleteRecursively(root)
    }

    @Test
    fun `missing annotated video fails with a clear error`() {
        val root = Files.createTempDirectory("session-export-store-missing-video")
        val summaryDir = root.resolve("session-summaries")
        val telemetryDir = root.resolve("telemetry")
        val videoDir = root.resolve("squat-videos")
        Files.createDirectories(summaryDir)
        Files.createDirectories(telemetryDir)
        val summaryJson = TelemetryCodec().encodeSummary(
            summary(
                sessionId = "squat-video-missing",
                telemetryFileName = "squat-video-missing.jsonl",
                annotatedVideoFileName = "squat-video-missing.mp4",
            ),
        )
        Files.write(summaryDir.resolve("squat-video-missing.json"), summaryJson.toByteArray(StandardCharsets.UTF_8))
        Files.write(telemetryDir.resolve("squat-video-missing.jsonl"), "{}\n".toByteArray(StandardCharsets.UTF_8))
        val store = SessionExportStore(summaryDir, telemetryDir, root.resolve("session-exports"), videoDir)

        val failure = assertThrows(IllegalStateException::class.java) {
            store.exportSession("squat-video-missing")
        }

        assertEquals("Annotated video missing for squat-video-missing", failure.message)
        deleteRecursively(root)
    }

    private fun summary(
        sessionId: String,
        telemetryFileName: String,
        annotatedVideoFileName: String? = null,
    ): SessionSummaryRecord =
        SessionSummaryRecord(
            sessionId = sessionId,
            activityType = ActivityType.SQUATS,
            telemetryFileName = telemetryFileName,
            completion = SessionCompletion.COMPLETE,
            frameCount = 2L,
            startedAtUtc = "2026-07-12T23:00:00Z",
            endedAtUtc = "2026-07-12T23:01:00Z",
            details = SquatSessionDetails(
                view = "side",
                annotatedVideoFileName = annotatedVideoFileName,
            ),
        )

    private fun ZipFile.readText(entryName: String): String =
        getInputStream(checkNotNull(getEntry(entryName))).use { input ->
            String(input.readBytes(), StandardCharsets.UTF_8)
        }

    private fun ZipFile.readBytes(entryName: String): ByteArray =
        getInputStream(checkNotNull(getEntry(entryName))).use { input ->
            input.readBytes()
        }

    private fun deleteRecursively(path: Path) {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }
}
