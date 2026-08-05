/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.activity

import android.graphics.Bitmap

enum class ActivityType {
    SQUATS,
}

data class ActivitySession(
    val id: String,
    val activityType: ActivityType,
    val startedAtEpochMs: Long,
    val startedElapsedRealtimeMs: Long,
)

/**
 * One shared, already-upright camera frame handed to whichever [ActivityBackend]
 * is currently active. [frameIndex] is the camera pipeline's own running frame
 * counter (not session-relative - backends track their own session-scoped frame
 * indices for telemetry). [capturedAtElapsedRealtimeMs] uses the same clock as
 * [ActivitySession.startedElapsedRealtimeMs] so a backend can compute exact
 * session-elapsed time for live frames without wall-clock drift.
 * [sourceVideoTimeMs] is reserved for imported footage and is null for live
 * camera frames.
 */
data class CameraFrame(
    val bitmap: Bitmap,
    val frameIndex: Long,
    val capturedAtEpochMs: Long,
    val capturedAtElapsedRealtimeMs: Long,
    val sourceVideoTimeMs: Long? = null,
)

sealed interface ActivityFrameResult

sealed interface ActivitySessionSummary {
    val sessionId: String
    val activityType: ActivityType
    val telemetryFileName: String
    val completion: SessionCompletion
}

enum class SessionCompletion {
    COMPLETE,
    INCOMPLETE,
}

interface ActivityBackend : AutoCloseable {
    val activityType: ActivityType
    suspend fun initialize()
    fun startSession(session: ActivitySession)
    fun analyzeFrame(frame: CameraFrame): ActivityFrameResult
    suspend fun endSession(): ActivitySessionSummary?
}
