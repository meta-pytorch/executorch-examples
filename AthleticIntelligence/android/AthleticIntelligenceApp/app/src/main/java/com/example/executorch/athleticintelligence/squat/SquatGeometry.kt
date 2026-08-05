/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * "GEOMETRY UTILITIES" section: `calc_angle`, `angle_from_vertical`,
 * `valid_xy`, `landmark_visibility`, `_knee_angle_for_side`, and
 * `_tracking_knee_angle`. No Android/OpenCV dependency.
 */
object SquatGeometry {

    const val MIN_VISIBILITY: Double = 0.3

    /**
     * Interior angle at vertex `b` for points `a -> b -> c`, in degrees [0, 180].
     * Mirrors `calc_angle` (dot-product method for numerical stability).
     */
    fun calcAngle(a: Pair<Double, Double>, b: Pair<Double, Double>, c: Pair<Double, Double>): Double {
        val baX = a.first - b.first
        val baY = a.second - b.second
        val bcX = c.first - b.first
        val bcY = c.second - b.second
        val dot = baX * bcX + baY * bcY
        val cosVal = dot / (hypot(baX, baY) * hypot(bcX, bcY) + 1e-8)
        return Math.toDegrees(acos(cosVal.coerceIn(-1.0, 1.0)))
    }

    /** Angle of the `top -> bottom` line vs. the vertical axis. 0deg = upright, + = lean right. Mirrors `angle_from_vertical`. */
    fun angleFromVertical(top: Pair<Double, Double>, bottom: Pair<Double, Double>): Double {
        val dx = bottom.first - top.first
        val dy = bottom.second - top.second
        return Math.toDegrees(atan2(dx, dy))
    }

    /** Mirrors `valid_xy`: returns null if the landmark is missing or below [minVisibility]. */
    fun validXy(landmarks: Landmarks, name: String, minVisibility: Double = MIN_VISIBILITY): Pair<Double, Double>? {
        val p = landmarks[name] ?: return null
        if (p.visibility < minVisibility) return null
        return p.xPx to p.yPx
    }

    /** Mirrors `landmark_visibility`: fraction of [names] present with visibility >= [MIN_VISIBILITY]. */
    fun landmarkVisibility(landmarks: Landmarks, names: List<String>): Double {
        if (names.isEmpty()) return 0.0
        val visible = names.count { name -> (landmarks[name]?.visibility ?: 0.0) >= MIN_VISIBILITY }
        return visible.toDouble() / names.size
    }

    /** Sum of raw visibility values for [names] (unclamped), mirroring the `sum(lm.get(name, (0,0,0))[2] ...)` pattern. */
    fun visibilitySum(landmarks: Landmarks, names: List<String>): Double =
        names.sumOf { landmarks[it]?.visibility ?: 0.0 }

    /** Mirrors `_knee_angle_for_side`: interior hip-knee-ankle angle for one side, or null if any point is invalid. */
    fun kneeAngleForSide(landmarks: Landmarks, side: String): Double? {
        val hip = validXy(landmarks, "${side}_hip") ?: return null
        val knee = validXy(landmarks, "${side}_knee") ?: return null
        val ankle = validXy(landmarks, "${side}_ankle") ?: return null
        return calcAngle(hip, knee, ankle)
    }

    /**
     * Mirrors `_tracking_knee_angle`. For [SquatView.SIDE], picks whichever side has
     * more total (unclamped) visibility across hip/knee/ankle and falls back to the
     * other side if the preferred side's angle can't be computed. For front/back,
     * averages both sides' angles (whichever are available).
     */
    fun trackingKneeAngle(landmarks: Landmarks, view: SquatView): Double? {
        if (view == SquatView.SIDE) {
            val rVis = visibilitySum(landmarks, listOf("r_hip", "r_knee", "r_ankle"))
            val lVis = visibilitySum(landmarks, listOf("l_hip", "l_knee", "l_ankle"))
            val preferred = if (rVis >= lVis) "r" else "l"
            val fallback = if (preferred == "r") "l" else "r"
            return kneeAngleForSide(landmarks, preferred) ?: kneeAngleForSide(landmarks, fallback)
        }

        val angles = listOfNotNull(kneeAngleForSide(landmarks, "l"), kneeAngleForSide(landmarks, "r"))
        if (angles.isEmpty()) return null
        return angles.average()
    }
}
