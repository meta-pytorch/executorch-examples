/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Port of `tests/test_squat_core_scoring.py::FrameMetricTests` plus a new
 * fixture (not present upstream) that pins down COCO-17 behavior described in
 * A reduced landmark set has no heel/foot-index keypoints, so the
 * side-view `heel_lift` and `knee_over_toes` metrics must be reported as
 * [MetricAvailability.NOT_APPLICABLE] rather than merely missing/insufficient.
 */
class SquatMetricsTest {

    private fun lm(x: Double, y: Double, visibility: Double = 0.99) = Landmark(x, y, visibility)

    private fun baseLandmarks(): Landmarks = mapOf(
        "l_shoulder" to lm(90.0, 80.0),
        "r_shoulder" to lm(210.0, 80.0),
        "l_hip" to lm(100.0, 180.0),
        "r_hip" to lm(200.0, 180.0),
        "l_knee" to lm(100.0, 280.0),
        "r_knee" to lm(200.0, 280.0),
        "l_ankle" to lm(100.0, 380.0),
        "r_ankle" to lm(200.0, 380.0),
        "l_heel" to lm(95.0, 390.0),
        "r_heel" to lm(205.0, 390.0),
        "l_foot" to lm(90.0, 385.0),
        "r_foot" to lm(210.0, 385.0),
    )

    // COCO-17 exposes no heel/foot-index points at all: those keys are absent
    // from the landmark map entirely (not merely low-visibility).
    private fun coco17Landmarks(): Landmarks = mapOf(
        "l_shoulder" to lm(90.0, 80.0),
        "r_shoulder" to lm(210.0, 80.0),
        "l_hip" to lm(100.0, 180.0),
        "r_hip" to lm(200.0, 180.0),
        "l_knee" to lm(100.0, 280.0),
        "r_knee" to lm(200.0, 280.0),
        "l_ankle" to lm(100.0, 380.0),
        "r_ankle" to lm(200.0, 380.0),
    )

    @Test
    fun `front view uses average of valid left and right knee angles`() {
        val metrics = extractFrameMetrics(baseLandmarks(), 300.0, 420.0, SquatView.FRONT, SquatPhase.DESCENDING)

        assertEquals(SquatView.FRONT, metrics.view)
        assertNotNull(metrics.trackingKneeAngleDeg)
        assertTrue(metrics.visibilityCoverage > 0.95)
        assertEquals(MetricAvailability.AVAILABLE, metrics.availability["knee_valgus_l"])
        assertEquals(MetricAvailability.AVAILABLE, metrics.availability["knee_valgus_r"])
    }

    @Test
    fun `front view falls back to one knee angle when the other side is not visible`() {
        val landmarks = baseLandmarks() + ("r_knee" to lm(200.0, 280.0, visibility = 0.05))

        val metrics = extractFrameMetrics(landmarks, 300.0, 420.0, SquatView.FRONT, SquatPhase.DESCENDING)

        assertNotNull(metrics.trackingKneeAngleDeg)
        assertTrue(metrics.visibilityCoverage < 0.95)
        assertTrue(metrics.availability["knee_valgus_r"] != MetricAvailability.AVAILABLE)
    }

    @Test
    fun `side view marks depth and trunk metrics available but excludes front-only valgus`() {
        val metrics = extractFrameMetrics(baseLandmarks(), 300.0, 420.0, SquatView.SIDE, SquatPhase.AT_DEPTH)

        assertEquals(SquatView.SIDE, metrics.view)
        assertNotNull(metrics.trackingKneeAngleDeg)
        assertEquals(MetricAvailability.AVAILABLE, metrics.availability["depth_angle"])
        assertEquals(MetricAvailability.AVAILABLE, metrics.availability["trunk_tibia"])
        assertTrue(metrics.availability["knee_valgus_l"] == null)
    }

    @Test
    fun `back view excludes depth and front valgus metrics`() {
        val metrics = extractFrameMetrics(baseLandmarks(), 300.0, 420.0, SquatView.BACK, SquatPhase.DESCENDING)

        assertEquals(SquatView.BACK, metrics.view)
        assertNotNull(metrics.trackingKneeAngleDeg)
        assertEquals(MetricAvailability.AVAILABLE, metrics.availability["shoulder_asymmetry"])
        assertEquals(MetricAvailability.AVAILABLE, metrics.availability["heel_asymmetry"])
        assertTrue(metrics.availability["depth_angle"] == null)
        assertTrue(metrics.availability["knee_valgus_l"] == null)
    }

    @Test
    fun `side view marks heel lift and knee over toes not applicable for COCO-17 landmarks`() {
        val metrics = extractFrameMetrics(coco17Landmarks(), 300.0, 420.0, SquatView.SIDE, SquatPhase.AT_DEPTH)

        assertEquals(MetricAvailability.NOT_APPLICABLE, metrics.availability["heel_lift"])
        assertEquals(MetricAvailability.NOT_APPLICABLE, metrics.availability["knee_over_toes"])
        // Depth and trunk-tibia only need hip/knee/ankle/shoulder, which COCO-17 provides.
        assertEquals(MetricAvailability.AVAILABLE, metrics.availability["depth_angle"])
        assertEquals(MetricAvailability.AVAILABLE, metrics.availability["trunk_tibia"])
    }
}
