/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class DashboardDestination(val label: String) {
    HOME("Live"),
    INSIGHTS("Insights"),
    TIMELINE("Timeline"),
}

enum class TimelineSegment {
    SESSION,
    MISTAKES,
}

enum class TrendPeriod(val days: Int, val label: String) {
    WEEK(7, "Week"),
    MONTH(30, "Month"),
}

data class IssueCount(val label: String, val count: Int)

fun formatCaptureDateTime(
    capturedAtEpochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(capturedAtEpochMs)
    .atZone(zoneId)
    .format(DateTimeFormatter.ofPattern("MMM d, uuuu • h:mm a", Locale.US))
