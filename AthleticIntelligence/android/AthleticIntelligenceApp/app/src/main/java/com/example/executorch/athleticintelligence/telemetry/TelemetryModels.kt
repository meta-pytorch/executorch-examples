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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelemetryEnvelope(
    @SerialName("schema_version") val schemaVersion: Int = 2,
    @SerialName("event_type") val eventType: String = "frame",
    @SerialName("timestamp_utc") val timestampUtc: String,
    @SerialName("session_elapsed_ms") val sessionElapsedMs: Long,
    @SerialName("source_video_time_ms") val sourceVideoTimeMs: Long? = null,
    @SerialName("frame_index") val frameIndex: Long,
    @SerialName("source") val source: TelemetrySource,
    @SerialName("activity_type") val activityType: ActivityType,
    @SerialName("payload") val payload: ActivityTelemetryPayload,
)

@Serializable
enum class TelemetrySource {
    @SerialName("live_camera") LIVE_CAMERA,
}

@Serializable
sealed interface ActivityTelemetryPayload

@Serializable
data class TelemetryLandmark(
    @SerialName("x_px") val xPx: Double,
    @SerialName("y_px") val yPx: Double,
    @SerialName("visibility") val visibility: Double? = null,
)

@Serializable
data class SquatTelemetryAlert(
    @SerialName("level") val level: String = "SAFE",
    @SerialName("severity") val severity: Int = 0,
    @SerialName("metric") val metric: String = "",
    @SerialName("message") val message: String = "",
    @SerialName("value") val value: Double = 0.0,
    @SerialName("view_label") val viewLabel: String = "",
)

@Serializable
@SerialName("squat")
data class SquatTelemetryPayload(
    @SerialName("view") val view: String? = null,
    @SerialName("phase") val phase: String? = null,
    @SerialName("rep_count") val repCount: Int? = null,
    @SerialName("active_rep_number") val activeRepNumber: Int? = null,
    @SerialName("alerts") val alerts: List<SquatTelemetryAlert> = emptyList(),
    @SerialName("landmarks") val landmarks: Map<String, TelemetryLandmark> = emptyMap(),
    @SerialName("metrics") val metrics: Map<String, Double> = emptyMap(),
    @SerialName("metric_availability") val metricAvailability: Map<String, String> = emptyMap(),
) : ActivityTelemetryPayload

interface SessionTelemetryWriter {
    fun enqueue(envelope: TelemetryEnvelope): Result<Unit>
    suspend fun finish(): TelemetryArtifact
    suspend fun fail(cause: Throwable): TelemetryArtifact
    fun abort(cause: Throwable)
}

data class TelemetryArtifact(
    val fileName: String,
    val frameCount: Long,
    val completion: SessionCompletion,
    val errorMessage: String?,
)

@Serializable
sealed interface ActivitySessionDetails

@Serializable
@SerialName("squat")
data class SquatSessionDetails(
    @SerialName("view") val view: String? = null,
    @SerialName("annotated_video_file_name") val annotatedVideoFileName: String? = null,
    @SerialName("reps") val reps: List<SquatRepSummary> = emptyList(),
) : ActivitySessionDetails

@Serializable
data class SquatRepSummary(
    @SerialName("rep_number") val repNumber: Int,
    @SerialName("grade") val grade: String,
    @SerialName("min_knee_angle_deg") val minKneeAngleDeg: Double,
    @SerialName("score") val score: Int,
    @SerialName("confidence_coverage") val confidenceCoverage: Double,
    @SerialName("primary_correction") val primaryCorrection: String,
    @SerialName("secondary_corrections") val secondaryCorrections: List<String> = emptyList(),
    @SerialName("issue_severities") val issueSeverities: Map<String, Int> = emptyMap(),
    @SerialName("issue_messages") val issueMessages: Map<String, String> = emptyMap(),
    @SerialName("metric_summaries") val metricSummaries: Map<String, SquatMetricSummary> = emptyMap(),
)

@Serializable
data class SquatMetricSummary(
    @SerialName("metric") val metric: String,
    @SerialName("valid_frames") val validFrames: Int = 0,
    @SerialName("total_frames") val totalFrames: Int = 0,
    @SerialName("mean") val mean: Double? = null,
    @SerialName("min_value") val minValue: Double? = null,
    @SerialName("max_value") val maxValue: Double? = null,
    @SerialName("warning_persistence") val warningPersistence: Double = 0.0,
    @SerialName("danger_persistence") val dangerPersistence: Double = 0.0,
    @SerialName("severity_area") val severityArea: Double = 0.0,
    @SerialName("contribution") val contribution: Double = 0.0,
    @SerialName("message") val message: String = "",
    @SerialName("status") val status: String = "INSUFFICIENT_DATA",
)

@Serializable
data class SessionSummaryRecord(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("session_id") val sessionId: String,
    @SerialName("activity_type") val activityType: ActivityType,
    @SerialName("telemetry_file_name") val telemetryFileName: String,
    @SerialName("completion") val completion: SessionCompletion,
    @SerialName("frame_count") val frameCount: Long,
    @SerialName("started_at_utc") val startedAtUtc: String,
    @SerialName("ended_at_utc") val endedAtUtc: String,
    @SerialName("metrics") val metrics: Map<String, Double> = emptyMap(),
    @SerialName("notes") val notes: List<String> = emptyList(),
    @SerialName("details") val details: ActivitySessionDetails? = null,
)
