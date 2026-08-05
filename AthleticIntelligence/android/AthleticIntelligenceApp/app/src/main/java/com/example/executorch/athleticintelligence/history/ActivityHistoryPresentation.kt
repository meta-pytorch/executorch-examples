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
import com.example.executorch.athleticintelligence.telemetry.SquatSessionDetails
import com.example.executorch.athleticintelligence.ui.IssueCount
import com.example.executorch.athleticintelligence.ui.TrendPeriod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class ActivitySessionHistoryRow(
    val sessionId: String,
    val title: String,
    val detail: String,
    val startedAtUtc: String,
    val startedAtEpochMs: Long,
    val actionCount: Int,
    val status: String,
)

/**
 * [title] is the correction category (for example, "Keep knees tracking over
 * toes") and [detail] is the per-instance context (for example, "Rep 2").
 */
data class ActivityMistakeHistoryRow(
    val sessionId: String,
    val title: String,
    val detail: String,
    val severity: Int,
    val actionNumber: Int? = null,
    val capturedAtEpochMs: Long? = null,
    val timestampMs: Long? = null,
)

data class ActivityHistoryPresentation(
    val activityType: ActivityType,
    val sessionRows: List<ActivitySessionHistoryRow>,
    val mistakeRows: List<ActivityMistakeHistoryRow>,
    val totalActions: Int,
    val averageScore: Int?,
)

/** Mirrors [com.example.executorch.athleticintelligence.intel.DailyTrendPoint] but with activity-agnostic naming. */
data class ActivityDailyTrendPoint(
    val dayStartEpochMs: Long,
    val actionCount: Int,
    val affectedActionCount: Int,
    val issueRatePercent: Int,
)

/** Mirrors [com.example.executorch.athleticintelligence.intel.TrendWindow] but with activity-agnostic naming. */
data class ActivityTrendWindow(
    val period: TrendPeriod,
    val actionCount: Int,
    val affectedActionCount: Int,
    val issueRatePercent: Int,
)

data class ActivityInsightSnapshot(
    val recentIssueCounts: List<IssueCount>,
    val topIssues: List<ActivityMistakeHistoryRow>,
    val trendWindows: List<ActivityTrendWindow>,
    val dailyTrendPoints: List<ActivityDailyTrendPoint>,
    val averageScore: Int?,
)

fun buildActivityHistoryPresentation(
    activityType: ActivityType,
    summaries: List<SessionSummaryRecord>,
): ActivityHistoryPresentation {
    val matching = summaries
        .filter { it.activityType == activityType }
        .sortedByDescending { it.startedAtUtc }
    return ActivityHistoryPresentation(
        activityType = activityType,
        sessionRows = matching.map { it.toSessionRow() },
        mistakeRows = matching.flatMap { it.toMistakeRows() },
        totalActions = matching.sumOf { it.actionCount() },
        averageScore = matching
            .mapNotNull { it.metrics["average_score"] }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt(),
    )
}

/**
 * Day-bucketed trend/issue-rate data for the Insights tab, generalizing
 * [com.example.executorch.athleticintelligence.intel.buildInsightSnapshot]'s bucketing logic to work from
 * [SessionSummaryRecord]s directly so the trend chart and issue summaries are
 * derived from persisted squat-session data.
 */
fun buildActivityInsightSnapshot(
    activityType: ActivityType,
    summaries: List<SessionSummaryRecord>,
    period: TrendPeriod = TrendPeriod.WEEK,
    nowEpochMs: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): ActivityInsightSnapshot {
    val matching = summaries
        .filter { it.activityType == activityType }
    val today = nowEpochMs.toLocalDate(zoneId)
    val selected = matching.inPeriod(period, today, zoneId)
    return ActivityInsightSnapshot(
        recentIssueCounts = issueCounts(selected.flatMap { it.toMistakeRows() }),
        topIssues = matching.flatMap { it.toMistakeRows() }
            .sortedWith(compareByDescending<ActivityMistakeHistoryRow> { it.severity }.thenBy { it.timestampMs ?: 0L })
            .take(5),
        trendWindows = TrendPeriod.entries.map { trendPeriod ->
            buildTrendWindow(trendPeriod, matching.inPeriod(trendPeriod, today, zoneId))
        },
        dailyTrendPoints = buildDailyTrendPoints(selected, period, today, zoneId),
        averageScore = selected
            .mapNotNull { it.metrics["average_score"] }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.roundToInt(),
    )
}

private fun SessionSummaryRecord.toSessionRow(): ActivitySessionHistoryRow {
    val actionCount = actionCount()
    val detail = "$actionCount ${actionCount.pluralized("rep", "reps")}" +
        metrics["average_score"]?.let { " • ${it.roundToInt()} average score" }.orEmpty()
    return ActivitySessionHistoryRow(
        sessionId = sessionId,
        title = "Squat session",
        detail = detail,
        startedAtUtc = startedAtUtc,
        startedAtEpochMs = startedAtUtc.toEpochMillisOrZero(),
        actionCount = actionCount,
        status = if (completion == SessionCompletion.COMPLETE) "Complete" else "Collection incomplete",
    )
}

private fun SessionSummaryRecord.toMistakeRows(): List<ActivityMistakeHistoryRow> =
    (details as? SquatSessionDetails)
            ?.reps
            .orEmpty()
            .filter { it.primaryCorrection.isNotBlank() && it.primaryCorrection != "Good form" }
            .map { rep ->
                ActivityMistakeHistoryRow(
                    sessionId = sessionId,
                    title = rep.primaryCorrection,
                    detail = "Rep ${rep.repNumber}",
                    severity = rep.issueSeverities.values.maxOrNull() ?: 1,
                    actionNumber = rep.repNumber,
                    capturedAtEpochMs = startedAtUtc.toEpochMillisOrZero(),
                )
            }

private fun SessionSummaryRecord.actionCount(): Int =
    (details as? SquatSessionDetails)?.reps?.size
        ?: metrics["rep_count"]?.roundToInt()
        ?: 0

private fun buildTrendWindow(period: TrendPeriod, summaries: List<SessionSummaryRecord>): ActivityTrendWindow {
    val actionCount = summaries.sumOf { it.actionCount() }
    val affectedActionCount = affectedActionCount(summaries)
    return ActivityTrendWindow(
        period = period,
        actionCount = actionCount,
        affectedActionCount = affectedActionCount,
        issueRatePercent = issueRatePercent(affectedActionCount, actionCount),
    )
}

private fun buildDailyTrendPoints(
    summaries: List<SessionSummaryRecord>,
    period: TrendPeriod,
    today: LocalDate,
    zoneId: ZoneId,
): List<ActivityDailyTrendPoint> {
    val startDate = today.minusDays(period.days.toLong() - 1L)
    val summariesByDate = summaries.groupBy { it.startedAtUtc.toEpochMillisOrZero().toLocalDate(zoneId) }
    val points = mutableListOf<ActivityDailyTrendPoint>()
    var date = startDate
    while (!date.isAfter(today)) {
        val daySummaries = summariesByDate[date].orEmpty()
        val actionCount = daySummaries.sumOf { it.actionCount() }
        val affectedActionCount = affectedActionCount(daySummaries)
        points += ActivityDailyTrendPoint(
            dayStartEpochMs = date.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            actionCount = actionCount,
            affectedActionCount = affectedActionCount,
            issueRatePercent = issueRatePercent(affectedActionCount, actionCount),
        )
        date = date.plusDays(1)
    }
    return points
}

/** Distinct flagged repetitions across [summaries], by session and rep number. */
private fun affectedActionCount(summaries: List<SessionSummaryRecord>): Int =
    summaries.sumOf { summary ->
        summary.toMistakeRows().mapNotNull { it.actionNumber }.distinct().size
    }

private fun issueRatePercent(affectedActionCount: Int, actionCount: Int): Int =
    if (actionCount == 0) 0 else affectedActionCount * 100 / actionCount

private fun List<SessionSummaryRecord>.inPeriod(
    period: TrendPeriod,
    today: LocalDate,
    zoneId: ZoneId,
): List<SessionSummaryRecord> {
    val startDate = today.minusDays(period.days.toLong() - 1L)
    return filter {
        val startedDate = it.startedAtUtc.toEpochMillisOrZero().toLocalDate(zoneId)
        !startedDate.isBefore(startDate) && !startedDate.isAfter(today)
    }
}

private fun issueCounts(rows: List<ActivityMistakeHistoryRow>): List<IssueCount> =
    rows
        .groupingBy { it.title }
        .eachCount()
        .map { IssueCount(it.key, it.value) }
        .sortedWith(compareByDescending<IssueCount> { it.count }.thenBy { it.label })

private fun String.toEpochMillisOrZero(): Long =
    runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0L)

private fun Long.toLocalDate(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

private fun Number.pluralized(singular: String, plural: String): String =
    if (toLong() == 1L) singular else plural
