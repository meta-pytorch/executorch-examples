/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.history

import com.example.executorch.athleticintelligence.activity.ActivityType
import com.example.executorch.athleticintelligence.activity.SessionCompletion
import com.example.executorch.athleticintelligence.telemetry.SessionSummaryRecord
import com.example.executorch.athleticintelligence.telemetry.SquatRepSummary
import com.example.executorch.athleticintelligence.telemetry.SquatSessionDetails
import com.example.executorch.athleticintelligence.telemetry.TelemetryCodec
import com.example.executorch.athleticintelligence.ui.IssueCount
import com.example.executorch.athleticintelligence.ui.TrendPeriod
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityHistoryPresentationTest {
    private val codec = TelemetryCodec()

    @Test
    fun `encoded summaries produce squat rep and correction rows`() {
        val summary = codec.decodeSummary(codec.encodeSummary(squatSummary()))

        val presentation = buildActivityHistoryPresentation(ActivityType.SQUATS, listOf(summary))

        assertEquals(listOf("squat-1"), presentation.sessionRows.map { it.sessionId })
        assertEquals("Squat session", presentation.sessionRows.single().title)
        assertEquals("2 reps • 91 average score", presentation.sessionRows.single().detail)
        assertEquals(listOf("Keep knees tracking over toes"), presentation.mistakeRows.map { it.title })
        assertEquals("Rep 2", presentation.mistakeRows.single().detail)
    }

    @Test
    fun `rows are newest first and incomplete collection is visible`() {
        val older = squatSummary().copy(sessionId = "older", startedAtUtc = "2026-07-12T10:00:00Z")
        val newer = squatSummary().copy(
            sessionId = "newer",
            startedAtUtc = "2026-07-12T12:00:00Z",
            completion = SessionCompletion.INCOMPLETE,
        )

        val rows = buildActivityHistoryPresentation(ActivityType.SQUATS, listOf(older, newer)).sessionRows

        assertEquals(listOf("newer", "older"), rows.map { it.sessionId })
        assertEquals("Collection incomplete", rows.first().status)
    }

    @Test
    fun `insight snapshot computes squat issue rate and average score`() {
        val snapshot = buildActivityInsightSnapshot(
            activityType = ActivityType.SQUATS,
            summaries = listOf(squatSummary()),
            period = TrendPeriod.WEEK,
            nowEpochMs = Instant.parse("2026-07-12T23:59:59Z").toEpochMilli(),
            zoneId = ZoneOffset.UTC,
        )

        val week = snapshot.trendWindows.single { it.period == TrendPeriod.WEEK }
        assertEquals(2, week.actionCount)
        assertEquals(1, week.affectedActionCount)
        assertEquals(50, week.issueRatePercent)
        assertEquals(listOf(IssueCount("Keep knees tracking over toes", 1)), snapshot.recentIssueCounts)
        assertEquals(7, snapshot.dailyTrendPoints.size)
        assertEquals(91, snapshot.averageScore)
    }

    private fun squatSummary() = SessionSummaryRecord(
        sessionId = "squat-1",
        activityType = ActivityType.SQUATS,
        telemetryFileName = "squat-1.jsonl",
        completion = SessionCompletion.COMPLETE,
        frameCount = 80,
        startedAtUtc = "2026-07-12T11:00:00Z",
        endedAtUtc = "2026-07-12T11:01:00Z",
        metrics = mapOf("average_score" to 91.0),
        details = SquatSessionDetails(
            reps = listOf(
                SquatRepSummary(1, "GOOD", 98.0, 96, 0.98, "Good form"),
                SquatRepSummary(
                    repNumber = 2,
                    grade = "FAIR",
                    minKneeAngleDeg = 110.0,
                    score = 86,
                    confidenceCoverage = 0.9,
                    primaryCorrection = "Keep knees tracking over toes",
                    issueSeverities = mapOf("knee_valgus_l" to 1),
                ),
            ),
        ),
    )
}
