/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import kotlin.math.abs
import kotlin.math.max

/**
 * Thresholds, metric rules, and view-aware metric extraction ported from
 * `VIEW_METRICS`) and "GEOMETRY UTILITIES" (`extract_frame_metrics`)
 * sections. Values are copied verbatim; do not retune.
 */
object SquatThresholds {
    const val KNEE_VALGUS_W = 0.02
    const val KNEE_VALGUS_D = 0.05
    const val ASYMMETRY_W = 8.0
    const val ASYMMETRY_D = 15.0
    const val HIP_DROP_W = 0.02
    const val HIP_DROP_D = 0.05
    const val TRUNK_LEAN_W = 10.0
    const val TRUNK_LEAN_D = 20.0
    const val TRUNK_KNEE_W = -10.0
    const val TRUNK_KNEE_D = -20.0
    const val HEEL_LIFT_W = 0.01
    const val HEEL_LIFT_D = 0.03
    const val KNEE_TOES_W = 0.03
    const val KNEE_TOES_D = 0.08
    const val BUTT_WINK_W = 15.0
    const val BUTT_WINK_D = 25.0
    const val DEPTH_SHALLOW = 140.0
    const val DEPTH_DEEP = 70.0
    const val SPINE_ROT_W = 0.03
    const val SPINE_ROT_D = 0.06
}

const val TRUNK_TIBIA_KNEE_DOMINANT_MESSAGE = "Reduce knee-dominant loading"

val METRIC_RULES: Map<String, MetricRule> = mapOf(
    "knee_valgus_l" to MetricRule(SquatThresholds.KNEE_VALGUS_W, SquatThresholds.KNEE_VALGUS_D, 35, "Keep knees tracking over toes"),
    "knee_valgus_r" to MetricRule(SquatThresholds.KNEE_VALGUS_W, SquatThresholds.KNEE_VALGUS_D, 35, "Keep knees tracking over toes"),
    "asymmetry" to MetricRule(SquatThresholds.ASYMMETRY_W, SquatThresholds.ASYMMETRY_D, 18, "Balance depth between left and right legs"),
    "hip_drop" to MetricRule(SquatThresholds.HIP_DROP_W, SquatThresholds.HIP_DROP_D, 18, "Keep hips level through the rep"),
    "shoulder_asymmetry" to MetricRule(SquatThresholds.SPINE_ROT_W, SquatThresholds.SPINE_ROT_D, 12, "Keep shoulders level"),
    "heel_asymmetry" to MetricRule(SquatThresholds.HIP_DROP_W, SquatThresholds.HIP_DROP_D, 14, "Keep both heels grounded evenly"),
    "depth_angle" to MetricRule(SquatThresholds.DEPTH_SHALLOW, 155.0, 30, "Squat deeper with control"),
    "trunk_tibia" to MetricRule(SquatThresholds.TRUNK_LEAN_W, SquatThresholds.TRUNK_LEAN_D, 24, "Keep torso and shins balanced"),
    "heel_lift" to MetricRule(SquatThresholds.HEEL_LIFT_W, SquatThresholds.HEEL_LIFT_D, 20, "Keep heels down"),
    "knee_over_toes" to MetricRule(SquatThresholds.KNEE_TOES_W, SquatThresholds.KNEE_TOES_D, 14, "Shift hips back to reduce knee travel"),
)

val VIEW_METRICS: Map<SquatView, List<String>> = mapOf(
    SquatView.FRONT to listOf("knee_valgus_l", "knee_valgus_r", "asymmetry", "hip_drop"),
    SquatView.SIDE to listOf("depth_angle", "trunk_tibia", "heel_lift", "knee_over_toes"),
    SquatView.BACK to listOf("shoulder_asymmetry", "hip_drop", "heel_asymmetry"),
)

/** Mirrors `_level`: "higher is worse" danger classification. */
fun levelGe(value: Double, warn: Double, danger: Double): DangerLevel = when {
    value >= danger -> DangerLevel.DANGER
    value >= warn -> DangerLevel.WARNING
    else -> DangerLevel.SAFE
}

/** Mirrors `_level_le`: "lower is worse" danger classification. */
fun levelLe(value: Double, warn: Double, danger: Double): DangerLevel = when {
    value <= danger -> DangerLevel.DANGER
    value <= warn -> DangerLevel.WARNING
    else -> DangerLevel.SAFE
}

/**
 * Mirrors `extract_frame_metrics`. Populates [FrameMetrics.values] for every metric
 * this frame could compute, and [FrameMetrics.availability] for every metric
 * relevant to [view]: [MetricAvailability.AVAILABLE] when computed,
 * [MetricAvailability.NOT_APPLICABLE] when a landmark key the metric depends on is
 * entirely absent from [landmarks] (e.g. COCO-17 has no heel/foot-index points, see
 * a reduced landmark set), or [MetricAvailability.INSUFFICIENT_DATA] when the keys exist
 * but visibility/geometry made the value uncomputable this frame.
 */
fun extractFrameMetrics(
    landmarks: Landmarks,
    widthPx: Double,
    heightPx: Double,
    view: SquatView,
    phase: SquatPhase,
): FrameMetrics {
    val requiredForTracking = listOf("l_hip", "r_hip", "l_knee", "r_knee", "l_ankle", "r_ankle")
    val trackingAngle = SquatGeometry.trackingKneeAngle(landmarks, view)
    val visibilityCoverage = SquatGeometry.landmarkVisibility(landmarks, requiredForTracking)

    val values = mutableMapOf<String, Double>()
    val availability = mutableMapOf<String, MetricAvailability>()

    fun putMetric(name: String, value: Double?, requiredKeys: List<String>) {
        if (value != null) {
            values[name] = value
            availability[name] = MetricAvailability.AVAILABLE
        } else {
            availability[name] = if (requiredKeys.all { landmarks.containsKey(it) }) {
                MetricAvailability.INSUFFICIENT_DATA
            } else {
                MetricAvailability.NOT_APPLICABLE
            }
        }
    }

    when (view) {
        SquatView.FRONT -> {
            for (side in listOf("l", "r")) {
                val knee = SquatGeometry.validXy(landmarks, "${side}_knee")
                val ankle = SquatGeometry.validXy(landmarks, "${side}_ankle")
                val valgus = if (knee != null && ankle != null) {
                    if (side == "l") max(0.0, (knee.first - ankle.first) / widthPx) else max(0.0, (ankle.first - knee.first) / widthPx)
                } else {
                    null
                }
                putMetric("knee_valgus_$side", valgus, listOf("${side}_knee", "${side}_ankle"))
            }

            val lAngle = SquatGeometry.kneeAngleForSide(landmarks, "l")
            val rAngle = SquatGeometry.kneeAngleForSide(landmarks, "r")
            val asymmetry = if (lAngle != null && rAngle != null) abs(lAngle - rAngle) else null
            putMetric("asymmetry", asymmetry, listOf("l_hip", "l_knee", "l_ankle", "r_hip", "r_knee", "r_ankle"))

            val lHip = SquatGeometry.validXy(landmarks, "l_hip")
            val rHip = SquatGeometry.validXy(landmarks, "r_hip")
            val hipDrop = if (lHip != null && rHip != null) abs(lHip.second - rHip.second) / heightPx else null
            putMetric("hip_drop", hipDrop, listOf("l_hip", "r_hip"))
        }

        SquatView.SIDE -> {
            putMetric("depth_angle", trackingAngle, requiredForTracking)

            val rVis = SquatGeometry.visibilitySum(landmarks, listOf("r_hip", "r_knee", "r_ankle"))
            val lVis = SquatGeometry.visibilitySum(landmarks, listOf("l_hip", "l_knee", "l_ankle"))
            val side = if (rVis >= lVis) "r" else "l"

            val shoulder = SquatGeometry.validXy(landmarks, "${side}_shoulder")
            val hip = SquatGeometry.validXy(landmarks, "${side}_hip")
            val knee = SquatGeometry.validXy(landmarks, "${side}_knee")
            val ankle = SquatGeometry.validXy(landmarks, "${side}_ankle")
            val heel = SquatGeometry.validXy(landmarks, "${side}_heel")
            val foot = SquatGeometry.validXy(landmarks, "${side}_foot")

            val trunk = if (shoulder != null && hip != null) SquatGeometry.angleFromVertical(shoulder, hip) else null
            val tibia = if (knee != null && ankle != null) SquatGeometry.angleFromVertical(knee, ankle) else null
            putMetric("trunk_angle", trunk, listOf("${side}_shoulder", "${side}_hip"))

            val trunkTibia = if (trunk != null && tibia != null) trunk - tibia else null
            putMetric("trunk_tibia", trunkTibia, listOf("${side}_shoulder", "${side}_hip", "${side}_knee", "${side}_ankle"))

            val heelLift = if (heel != null && foot != null) (foot.second - heel.second) / heightPx else null
            putMetric("heel_lift", heelLift, listOf("${side}_heel", "${side}_foot"))

            val kneeOverToes = if (knee != null && foot != null) abs(knee.first - foot.first) / widthPx else null
            putMetric("knee_over_toes", kneeOverToes, listOf("${side}_knee", "${side}_foot"))
        }

        SquatView.BACK -> {
            val lShoulder = SquatGeometry.validXy(landmarks, "l_shoulder")
            val rShoulder = SquatGeometry.validXy(landmarks, "r_shoulder")
            val lHip = SquatGeometry.validXy(landmarks, "l_hip")
            val rHip = SquatGeometry.validXy(landmarks, "r_hip")
            val lHeel = SquatGeometry.validXy(landmarks, "l_heel")
            val rHeel = SquatGeometry.validXy(landmarks, "r_heel")

            val shoulderAsymmetry = if (lShoulder != null && rShoulder != null) abs(lShoulder.second - rShoulder.second) / heightPx else null
            putMetric("shoulder_asymmetry", shoulderAsymmetry, listOf("l_shoulder", "r_shoulder"))

            val hipDrop = if (lHip != null && rHip != null) abs(lHip.second - rHip.second) / heightPx else null
            putMetric("hip_drop", hipDrop, listOf("l_hip", "r_hip"))

            val heelAsymmetry = if (lHeel != null && rHeel != null) abs(lHeel.second - rHeel.second) / heightPx else null
            putMetric("heel_asymmetry", heelAsymmetry, listOf("l_heel", "r_heel"))
        }
    }

    return FrameMetrics(
        view = view,
        phase = phase,
        trackingKneeAngleDeg = trackingAngle,
        values = values,
        availability = availability,
        visibilityCoverage = visibilityCoverage,
    )
}
