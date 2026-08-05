/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.telemetry

import com.example.executorch.athleticintelligence.activity.ActivityType
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.stream.Collectors

class SessionSummaryStore(
    private val directory: Path,
    private val codec: TelemetryCodec = TelemetryCodec(),
    private val onBeforeRename: ((tmpFile: Path, finalFile: Path) -> Unit)? = null,
) {
    fun write(summary: SessionSummaryRecord) {
        Files.createDirectories(directory)
        val finalPath = directory.resolve("${summary.sessionId}.json")
        val tmpPath = directory.resolve("${summary.sessionId}.json.tmp")
        val payload = codec.encodeSummary(summary).toByteArray(StandardCharsets.UTF_8)

        FileChannel.open(
            tmpPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.write(ByteBuffer.wrap(payload))
            channel.force(true)
        }

        onBeforeRename?.invoke(tmpPath, finalPath)
        moveAtomically(tmpPath = tmpPath, finalPath = finalPath)
    }

    fun read(sessionId: String): SessionSummaryRecord? {
        val path = directory.resolve("$sessionId.json")
        if (!Files.exists(path)) return null
        val encoded = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        return codec.decodeSummary(encoded)
    }

    fun list(activityType: ActivityType? = null): List<SessionSummaryRecord> {
        if (!Files.exists(directory)) return emptyList()

        val decoded =
            Files.list(directory).use { paths ->
                paths
                    .filter { path -> path.fileName.toString().endsWith(".json") }
                    .sorted()
                    .map { path ->
                        runCatching {
                            codec.decodeSummary(String(Files.readAllBytes(path), StandardCharsets.UTF_8))
                        }.getOrNull()
                    }
                    .filter { summary -> summary != null }
                    .map { summary -> summary!! }
                    .collect(Collectors.toList())
            }

        return if (activityType == null) {
            decoded
        } else {
            decoded.filter { it.activityType == activityType }
        }
    }

    private fun moveAtomically(tmpPath: Path, finalPath: Path) {
        try {
            Files.move(
                tmpPath,
                finalPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmpPath, finalPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
