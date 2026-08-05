/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

/**
 * Pure-Kotlin, Android-free squat domain types. Geometry lives in
 * [SquatGeometry], metric extraction and thresholds live
 * in `SquatMetrics.kt`, phase transitions live in [SquatStateMachine], and
 * rep-level grading lives in `RepEvidenceTracker.kt`.
 *
 * Metric identifiers are stable telemetry keys; display copy is kept in the
 * presentation layer.
 */

/** Supported camera perspectives and their persisted wire names. */
enum class SquatView(val wireName: String) {
    FRONT("front"),
    SIDE("side"),
    BACK("back"),
}

/** Phases of one squat repetition. */
enum class SquatPhase {
    STANDING,
    DESCENDING,
    AT_DEPTH,
    ASCENDING,
}

/** Form-alert severity; declaration order matches [wireValue]. */
enum class DangerLevel(val wireValue: Int) {
    SAFE(0),
    WARNING(1),
    DANGER(2),
}

/** Overall grade for one completed repetition. */
enum class RepGrade {
    GOOD,
    FAIR,
    BAD,
}

/**
 * Distinguishes a valid measurement from a temporarily unavailable metric
 * and a metric that this landmark source can never provide. Pose backends can distinguish "never measurable from
 * this landmark source" ([NOT_APPLICABLE]) from "measurable in principle but
 * this frame/rep didn't have enough visible landmarks" ([INSUFFICIENT_DATA]).
 * [AVAILABLE] means that the current value can contribute to scoring.
 */
enum class MetricAvailability {
    AVAILABLE,
    INSUFFICIENT_DATA,
    NOT_APPLICABLE,
}

/**
 * Whether [SquatBackend] found a person in a live frame. A [PERSON_NOT_DETECTED]
 * frame still flows all the way through analysis and telemetry (as an
 * empty-[Landmarks] frame) - see `SquatFrameAnalyzer.processFrame` and
 * `SquatBackend`'s per-frame telemetry emission - it is never dropped.
 */
enum class TrackingStatus {
    TRACKED,
    PERSON_NOT_DETECTED,
}

/** Backward-compatible squat aliases over the shared COCO pose vocabulary. */
typealias Landmark = com.example.executorch.athleticintelligence.pose.PoseLandmark
typealias Landmarks = com.example.executorch.athleticintelligence.pose.PoseLandmarks

/**
 * Measurements extracted from one analyzed frame. [availability] records a
 * disposition for every metric relevant to [view].
 */
data class FrameMetrics(
    val view: SquatView,
    val phase: SquatPhase,
    val trackingKneeAngleDeg: Double?,
    val values: Map<String, Double>,
    val availability: Map<String, MetricAvailability>,
    val visibilityCoverage: Double,
) {
    /** Returns a value only when [availability] marks it available. */
    fun value(metric: String): Double? =
        if (availability[metric] == MetricAvailability.AVAILABLE) values[metric] else null
}

/** Thresholds and coaching copy for one scored metric. */
data class MetricRule(
    val warn: Double,
    val danger: Double,
    val weight: Int,
    val message: String,
    val lowerIsWorse: Boolean = false,
)

/** One immediate or accumulated form alert. */
data class Alert(
    val level: DangerLevel,
    val message: String,
    val metric: String,
    val value: Double,
    val viewLabel: String,
)

/** Aggregated evidence for one metric across a repetition. */
data class MetricSummary(
    val metric: String,
    val validFrames: Int,
    val totalFrames: Int,
    val mean: Double?,
    val minValue: Double?,
    val maxValue: Double?,
    val warningPersistence: Double,
    val dangerPersistence: Double,
    val severityArea: Double,
    val contribution: Double,
    val message: String,
    val status: MetricAvailability = MetricAvailability.AVAILABLE,
)

/** Persistable score and coaching evidence for one completed repetition. */
data class RepRecord(
    val repNumber: Int,
    val grade: RepGrade,
    val minKneeAngleDeg: Double,
    val issues: Map<String, DangerLevel>,
    val issueMessages: Map<String, String>,
    val score: Int = 100,
    val primaryCorrection: String = "Good form",
    val secondaryCorrections: List<String> = emptyList(),
    val confidenceCoverage: Double = 1.0,
    val metricSummaries: Map<String, MetricSummary> = emptyMap(),
)

/** Human-readable label persisted with alerts. */
val SquatView.label: String
    get() = wireName.replaceFirstChar { it.uppercase() }
