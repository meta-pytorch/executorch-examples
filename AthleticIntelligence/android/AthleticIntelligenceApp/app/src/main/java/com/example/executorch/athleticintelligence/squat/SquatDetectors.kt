/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

data class SideDetectionResult(
    val alerts: List<Alert>,
    val kneeAngleDeg: Double?,
)

/**
 * frame-local detector alerts used by the banner stream; rep evidence uses
 * the separate parity-stable rules in [RepEvidenceTracker].
 */
fun detectSide(
    landmarks: Landmarks,
    widthPx: Double,
    heightPx: Double,
    stateMachine: SquatStateMachine,
): SideDetectionResult {
    val alerts = mutableListOf<Alert>()

    fun visibility(name: String): Double = landmarks[name]?.visibility ?: 0.0
    val rightScore = visibility("r_hip") + visibility("r_knee") + visibility("r_ankle")
    val leftScore = visibility("l_hip") + visibility("l_knee") + visibility("l_ankle")
    val side = if (rightScore >= leftScore) "r" else "l"

    val shoulderName = "${side}_shoulder"
    val hipName = "${side}_hip"
    val kneeName = "${side}_knee"
    val ankleName = "${side}_ankle"
    val heelName = "${side}_heel"
    val footName = "${side}_foot"

    // Python's immediate detectors use `visibility > 0.3`, not the metric
    // extractor's `>= MIN_VISIBILITY` comparison.
    fun xy(name: String): Pair<Double, Double>? {
        val point = landmarks[name] ?: return null
        return if (point.visibility > 0.3) point.xPx to point.yPx else null
    }

    val hip = xy(hipName)
    val knee = xy(kneeName)
    val ankle = xy(ankleName)
    val kneeAngle = if (hip != null && knee != null && ankle != null) {
        SquatGeometry.calcAngle(hip, knee, ankle)
    } else {
        null
    }

    if (kneeAngle != null && stateMachine.isInSquat()) {
        if (kneeAngle > SquatThresholds.DEPTH_SHALLOW) {
            alerts += Alert(
                DangerLevel.WARNING,
                "Squat too shallow - go deeper",
                "depth",
                kneeAngle,
                "Side",
            )
        } else if (kneeAngle < SquatThresholds.DEPTH_DEEP) {
            alerts += Alert(
                DangerLevel.WARNING,
                "Excessively deep - lumbar risk",
                "depth",
                kneeAngle,
                "Side",
            )
        }
    }

    val shoulder = xy(shoulderName)
    val trunkInclination = if (shoulder != null && hip != null) {
        SquatGeometry.angleFromVertical(shoulder, hip)
    } else {
        null
    }
    val tibiaInclination = if (knee != null && ankle != null) {
        SquatGeometry.angleFromVertical(knee, ankle)
    } else {
        null
    }

    if (trunkInclination != null && tibiaInclination != null && stateMachine.isInSquat()) {
        val trunkTibia = trunkInclination - tibiaInclination
        if (trunkTibia > SquatThresholds.TRUNK_LEAN_W) {
            alerts += Alert(
                levelGe(trunkTibia, SquatThresholds.TRUNK_LEAN_W, SquatThresholds.TRUNK_LEAN_D),
                "Excessive forward trunk lean - lumbar spine at risk",
                "trunk_tibia",
                trunkTibia,
                "Side",
            )
        } else if (trunkTibia < SquatThresholds.TRUNK_KNEE_W) {
            alerts += Alert(
                levelLe(trunkTibia, SquatThresholds.TRUNK_KNEE_W, SquatThresholds.TRUNK_KNEE_D),
                "Excessive knee loading - reduce shin travel",
                "trunk_tibia",
                trunkTibia,
                "Side",
            )
        }
    }

    val heel = xy(heelName)
    val foot = xy(footName)
    if (heel != null && foot != null && stateMachine.isInSquat()) {
        val lift = (foot.second - heel.second) / heightPx
        if (lift > SquatThresholds.HEEL_LIFT_W) {
            alerts += Alert(
                levelGe(lift, SquatThresholds.HEEL_LIFT_W, SquatThresholds.HEEL_LIFT_D),
                "Heels lifting - ankle mobility deficit",
                "heel_lift",
                lift,
                "Side",
            )
        }
    }

    if (knee != null && foot != null && stateMachine.isInSquat()) {
        val over = abs(knee.first - foot.first) / widthPx
        if (over > SquatThresholds.KNEE_TOES_W) {
            alerts += Alert(
                levelGe(over, SquatThresholds.KNEE_TOES_W, SquatThresholds.KNEE_TOES_D),
                "Knees past toes - patellofemoral stress",
                "knee_over_toes",
                over,
                "Side",
            )
        }
    }

    val previousTrunkInclination = stateMachine.previousTrunkInclinationDeg
    if (
        stateMachine.isAtDepth() &&
        trunkInclination != null &&
        previousTrunkInclination != null
    ) {
        val tilt = abs(trunkInclination - previousTrunkInclination)
        if (tilt > SquatThresholds.BUTT_WINK_W) {
            alerts += Alert(
                levelGe(tilt, SquatThresholds.BUTT_WINK_W, SquatThresholds.BUTT_WINK_D),
                "Butt wink - lumbar flexion at squat bottom",
                "butt_wink",
                tilt,
                "Side",
            )
        }
    }
    stateMachine.previousTrunkInclinationDeg = trunkInclination

    return SideDetectionResult(alerts, kneeAngle)
}

fun detectFront(landmarks: Landmarks, widthPx: Double, heightPx: Double): List<Alert> {
    val alerts = mutableListOf<Alert>()

    fun xy(name: String): Pair<Double, Double>? {
        val point = landmarks[name] ?: return null
        return if (point.visibility > 0.3) point.xPx to point.yPx else null
    }

    for ((side, label) in listOf("l" to "Left", "r" to "Right")) {
        val knee = xy("${side}_knee")
        val ankle = xy("${side}_ankle")
        if (knee != null && ankle != null) {
            val valgus = if (side == "l") {
                max(0.0, (knee.first - ankle.first) / widthPx)
            } else {
                max(0.0, (ankle.first - knee.first) / widthPx)
            }
            if (valgus > SquatThresholds.KNEE_VALGUS_W) {
                alerts += Alert(
                    levelGe(valgus, SquatThresholds.KNEE_VALGUS_W, SquatThresholds.KNEE_VALGUS_D),
                    "$label knee valgus - knees caving inward, ACL risk",
                    "valgus_$side",
                    valgus,
                    "Front",
                )
            }
        }
    }

    val leftKnee = xy("l_knee")
    val rightKnee = xy("r_knee")
    val leftHip = xy("l_hip")
    val rightHip = xy("r_hip")
    val leftAnkle = xy("l_ankle")
    val rightAnkle = xy("r_ankle")
    if (
        leftKnee != null && rightKnee != null &&
        leftHip != null && rightHip != null &&
        leftAnkle != null && rightAnkle != null
    ) {
        val leftAngle = SquatGeometry.calcAngle(leftHip, leftKnee, leftAnkle)
        val rightAngle = SquatGeometry.calcAngle(rightHip, rightKnee, rightAnkle)
        val asymmetry = abs(leftAngle - rightAngle)
        if (asymmetry > SquatThresholds.ASYMMETRY_W) {
            alerts += Alert(
                levelGe(asymmetry, SquatThresholds.ASYMMETRY_W, SquatThresholds.ASYMMETRY_D),
                "L/R asymmetry (${String.format(Locale.US, "%.1f", asymmetry)}°) - imbalance risk",
                "asymmetry",
                asymmetry,
                "Front",
            )
        }
    }

    if (leftHip != null && rightHip != null) {
        val drop = abs(leftHip.second - rightHip.second) / heightPx
        if (drop > SquatThresholds.HIP_DROP_W) {
            alerts += Alert(
                levelGe(drop, SquatThresholds.HIP_DROP_W, SquatThresholds.HIP_DROP_D),
                "Hip drop - uneven weight distribution",
                "hip_drop",
                drop,
                "Front",
            )
        }
    }

    return alerts
}

fun detectBack(landmarks: Landmarks, widthPx: Double, heightPx: Double): List<Alert> {
    val alerts = mutableListOf<Alert>()

    fun xy(name: String): Pair<Double, Double>? {
        val point = landmarks[name] ?: return null
        return if (point.visibility > 0.3) point.xPx to point.yPx else null
    }

    val leftShoulder = xy("l_shoulder")
    val rightShoulder = xy("r_shoulder")
    if (leftShoulder != null && rightShoulder != null) {
        val rotation = abs(leftShoulder.second - rightShoulder.second) / heightPx
        if (rotation > SquatThresholds.SPINE_ROT_W) {
            alerts += Alert(
                levelGe(rotation, SquatThresholds.SPINE_ROT_W, SquatThresholds.SPINE_ROT_D),
                "Spine rotation - shoulders uneven",
                "spine_rot",
                rotation,
                "Back",
            )
        }
    }

    val leftHeel = xy("l_heel")
    val rightHeel = xy("r_heel")
    if (leftHeel != null && rightHeel != null) {
        val asymmetry = abs(leftHeel.second - rightHeel.second) / heightPx
        if (asymmetry > SquatThresholds.HIP_DROP_W) {
            alerts += Alert(
                levelGe(asymmetry, SquatThresholds.HIP_DROP_W, SquatThresholds.HIP_DROP_D),
                "Heel asymmetry - one foot lifting",
                "heel_asym",
                asymmetry,
                "Back",
            )
        }
    }

    return alerts
}
