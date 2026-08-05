/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `extract_frame_metrics` (see `tests/test_squat_core_scoring.py::FrameMetricTests`).
 */
class SquatGeometryTest {

    @Test
    fun `calc angle returns straight leg near 180 degrees`() {
        val hip = 100.0 to 100.0
        val knee = 100.0 to 200.0
        val ankle = 100.0 to 300.0

        val angle = SquatGeometry.calcAngle(hip, knee, ankle)

        assertTrue("expected ~180 but was $angle", Math.abs(angle - 180.0) < 0.01)
    }

    @Test
    fun `calc angle returns right angle for perpendicular legs`() {
        val hip = 100.0 to 100.0
        val knee = 100.0 to 200.0
        val ankle = 200.0 to 200.0

        val angle = SquatGeometry.calcAngle(hip, knee, ankle)

        assertTrue("expected ~90 but was $angle", Math.abs(angle - 90.0) < 0.01)
    }

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

    @Test
    fun `tracking knee angle averages both sides for front view`() {
        val angle = SquatGeometry.trackingKneeAngle(baseLandmarks(), SquatView.FRONT)
        assertTrue(angle != null)
    }

    @Test
    fun `tracking knee angle falls back to the visible side when one knee is occluded`() {
        val landmarks = baseLandmarks() + ("r_knee" to lm(200.0, 280.0, visibility = 0.05))

        val angle = SquatGeometry.trackingKneeAngle(landmarks, SquatView.FRONT)

        assertTrue(angle != null)
    }

    @Test
    fun `valid xy returns null below minimum visibility`() {
        val landmarks = mapOf("l_knee" to lm(100.0, 280.0, visibility = 0.05))

        assertNull(SquatGeometry.validXy(landmarks, "l_knee"))
    }

    @Test
    fun `landmark visibility is the fraction of requested landmarks above minimum visibility`() {
        val landmarks = mapOf(
            "l_hip" to lm(0.0, 0.0, visibility = 0.99),
            "r_hip" to lm(0.0, 0.0, visibility = 0.05),
        )

        val coverage = SquatGeometry.landmarkVisibility(landmarks, listOf("l_hip", "r_hip"))

        assertEquals(0.5, coverage, 1e-9)
    }
}
