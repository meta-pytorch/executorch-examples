/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.telemetry

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SessionExportArtifact(
    val file: File,
    val displayName: String,
    val mimeType: String = "application/zip",
)

class SessionExportStore(
    private val summaryDirectory: Path,
    private val telemetryDirectory: Path,
    private val exportDirectory: Path,
    private val videoDirectory: Path = defaultVideoDirectory(summaryDirectory),
    private val codec: TelemetryCodec = TelemetryCodec(),
) {
    fun exportSession(sessionId: String): SessionExportArtifact {
        val summaryPath = summaryDirectory.resolve("$sessionId.json")
        if (!Files.exists(summaryPath)) {
            throw IllegalStateException("Session summary missing for $sessionId")
        }

        val summaryJson = String(Files.readAllBytes(summaryPath), StandardCharsets.UTF_8)
        val summary = codec.decodeSummary(summaryJson)
        val telemetryPath = safeTelemetryPath(summary.telemetryFileName)
        if (telemetryPath == null || !Files.exists(telemetryPath)) {
            throw IllegalStateException("Session telemetry missing for $sessionId")
        }
        val videoFileName = (summary.details as? SquatSessionDetails)?.annotatedVideoFileName
        val videoPath = videoFileName?.let(::safeVideoPath)
        if (videoPath == null || !Files.exists(videoPath)) {
            throw IllegalStateException("Annotated video missing for $sessionId")
        }

        Files.createDirectories(exportDirectory)
        val displayName = "$sessionId.zip"
        val exportPath = exportDirectory.resolve(displayName)
        ZipOutputStream(
            Files.newOutputStream(
                exportPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            ),
        ).use { zip ->
            zip.writeEntry("summary.json", summaryJson.toByteArray(StandardCharsets.UTF_8))
            zip.writePathEntry("frames.jsonl", telemetryPath)
            zip.writePathEntry("annotated.mp4", videoPath)
        }

        return SessionExportArtifact(
            file = exportPath.toFile(),
            displayName = displayName,
        )
    }

    private fun safeTelemetryPath(fileName: String): Path? {
        return safePath(telemetryDirectory, fileName)
    }

    private fun safeVideoPath(fileName: String): Path? {
        return safePath(videoDirectory, fileName)
    }

    private fun safePath(root: Path, fileName: String): Path? {
        val safeRoot = root.toAbsolutePath().normalize()
        val safePath = safeRoot.resolve(fileName).normalize()
        return safePath.takeIf { it.startsWith(safeRoot) }
    }

    private fun ZipOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.writePathEntry(name: String, path: Path) {
        putNextEntry(ZipEntry(name))
        Files.newInputStream(path).use { input -> input.copyTo(this) }
        closeEntry()
    }
}

private fun defaultVideoDirectory(summaryDirectory: Path): Path =
    summaryDirectory.parent?.resolve("squat-videos")
        ?: summaryDirectory.resolve("squat-videos")
