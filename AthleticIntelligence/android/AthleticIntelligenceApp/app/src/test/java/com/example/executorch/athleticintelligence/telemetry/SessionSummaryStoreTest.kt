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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSummaryStoreTest {
    @Test
    fun `writes summary atomically via tmp file then rename`() {
        val outputDir = Files.createTempDirectory("summary-store-atomic")
        var observedTmpBeforeRename = false
        val store = SessionSummaryStore(
            directory = outputDir,
            onBeforeRename = { tmpFile, finalFile ->
                observedTmpBeforeRename = Files.exists(tmpFile) && !Files.exists(finalFile)
            },
        )

        store.write(summary(sessionId = "session-a", activityType = ActivityType.SQUATS))

        val finalFile = outputDir.resolve("session-a.json")
        val tmpFile = outputDir.resolve("session-a.json.tmp")

        assertTrue(observedTmpBeforeRename)
        assertTrue(Files.exists(finalFile))
        assertFalse(Files.exists(tmpFile))

        deleteRecursively(outputDir)
    }

    @Test
    fun `lists only committed squat json summaries`() {
        val outputDir = Files.createTempDirectory("summary-store-filter")
        val store = SessionSummaryStore(directory = outputDir)

        store.write(summary(sessionId = "squat-1", activityType = ActivityType.SQUATS))
        Files.write(outputDir.resolve("ignore.txt"), "not a summary".toByteArray(StandardCharsets.UTF_8))
        Files.write(outputDir.resolve("pending.json.tmp"), "not committed".toByteArray(StandardCharsets.UTF_8))

        val all = store.list()
        val squatOnly = store.list(activityType = ActivityType.SQUATS)

        assertEquals(1, all.size)
        assertEquals(listOf("squat-1"), squatOnly.map { it.sessionId })

        deleteRecursively(outputDir)
    }

    @Test
    fun `does not prune existing summaries`() {
        val outputDir = Files.createTempDirectory("summary-store-retention")
        val store = SessionSummaryStore(directory = outputDir)

        store.write(summary(sessionId = "session-1", activityType = ActivityType.SQUATS))
        store.write(summary(sessionId = "session-2", activityType = ActivityType.SQUATS))
        store.write(summary(sessionId = "session-3", activityType = ActivityType.SQUATS))

        val sessionIds = store.list(activityType = ActivityType.SQUATS).map { it.sessionId }

        assertEquals(listOf("session-1", "session-2", "session-3"), sessionIds)
        assertTrue(Files.exists(outputDir.resolve("session-1.json")))
        assertTrue(Files.exists(outputDir.resolve("session-2.json")))
        assertTrue(Files.exists(outputDir.resolve("session-3.json")))

        deleteRecursively(outputDir)
    }

    @Test
    fun `round trips every persisted squat rep detail without JSONL`() {
        val outputDir = Files.createTempDirectory("summary-store-squat-details")
        val store = SessionSummaryStore(directory = outputDir)
        val details = SquatSessionDetails(
            reps = listOf(
                SquatRepSummary(
                    repNumber = 1,
                    grade = "FAIR",
                    minKneeAngleDeg = 102.5,
                    score = 76,
                    confidenceCoverage = 0.91,
                    primaryCorrection = "Keep torso and shins balanced",
                    secondaryCorrections = listOf("Keep knees tracking over toes"),
                    issueSeverities = mapOf("trunk_tibia" to 1),
                    issueMessages = mapOf("trunk_tibia" to "Keep torso and shins balanced"),
                    metricSummaries = mapOf(
                        "trunk_tibia" to SquatMetricSummary(
                            metric = "trunk_tibia",
                            validFrames = 12,
                            totalFrames = 14,
                            mean = 15.0,
                            minValue = 8.0,
                            maxValue = 22.0,
                            warningPersistence = 0.5,
                            dangerPersistence = 0.1,
                            severityArea = 0.3,
                            contribution = 24.0,
                            message = "Keep torso and shins balanced",
                            status = "AVAILABLE",
                        ),
                        "heel_lift" to SquatMetricSummary(
                            metric = "heel_lift",
                            totalFrames = 14,
                            message = "Keep heels down",
                            status = "NOT_APPLICABLE",
                        ),
                    ),
                ),
            ),
        )
        val original = summary("squat-details", ActivityType.SQUATS).copy(details = details)

        store.write(original)
        val restored = store.read("squat-details")

        assertEquals(original, restored)
        val restoredDetails = restored!!.details as SquatSessionDetails
        assertEquals(details.reps.single(), restoredDetails.reps.single())
        assertEquals("squat-details.jsonl", restored.telemetryFileName)
        deleteRecursively(outputDir)
    }

    private fun summary(sessionId: String, activityType: ActivityType): SessionSummaryRecord =
        SessionSummaryRecord(
            sessionId = sessionId,
            activityType = activityType,
            telemetryFileName = "$sessionId.jsonl",
            completion = SessionCompletion.COMPLETE,
            frameCount = 120L,
            startedAtUtc = "2026-07-12T23:00:00Z",
            endedAtUtc = "2026-07-12T23:02:00Z",
            metrics = mapOf("confidence" to 0.92),
            notes = listOf("clean"),
        )

    private fun deleteRecursively(path: Path) {
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::deleteIfExists)
    }
}
