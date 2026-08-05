/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.activity

import com.example.executorch.athleticintelligence.squat.Alert
import com.example.executorch.athleticintelligence.squat.Landmarks
import com.example.executorch.athleticintelligence.squat.RepRecord
import com.example.executorch.athleticintelligence.squat.SquatPhase
import com.example.executorch.athleticintelligence.squat.SquatView
import com.example.executorch.athleticintelligence.squat.TrackingStatus

/**
 * Kotlin's sealed-interface rule requires every direct [ActivityFrameResult]
 * implementer to live in this package (`com.example.executorch.athleticintelligence.activity`), even though its
 * shape is squat-specific. [com.example.executorch.athleticintelligence.squat.SquatBackend]
 * imports this type directly while squat mechanics remain in the squat package.
 */
data class SquatActivityFrameResult(
    val phase: SquatPhase,
    val repCount: Int,
    val currentCorrection: String?,
    val latestScore: Int?,
    val landmarks: Landmarks,
    val tracking: TrackingStatus,
    val immediateAlerts: List<Alert> = emptyList(),
    val activeIssues: List<Alert> = emptyList(),
    val view: SquatView? = null,
) : ActivityFrameResult

/**
 * Direct [ActivitySessionSummary] implementer for squats, for the same
 * sealed-package reason as [SquatActivityFrameResult]. [repRecords] carries every
 * rep completed during the session (see `SquatBackend.endSession`) so in-process
 * callers (e.g. a live results screen) get the full graded rep list without
 * re-reading the persisted `SessionSummaryRecord`/JSONL telemetry from disk.
 */
data class SquatActivitySessionSummary(
    override val sessionId: String,
    override val telemetryFileName: String,
    override val completion: SessionCompletion,
    val repRecords: List<RepRecord>,
    val frameCount: Long,
    val startedAtUtc: String,
    val endedAtUtc: String,
    val annotatedVideoFileName: String? = null,
) : ActivitySessionSummary {
    override val activityType: ActivityType get() = ActivityType.SQUATS
}
