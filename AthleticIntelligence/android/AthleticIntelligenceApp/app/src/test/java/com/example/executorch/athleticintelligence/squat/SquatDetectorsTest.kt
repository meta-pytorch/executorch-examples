/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic ports of the immediate danger-detector behavior in
 */
class SquatDetectorsTest {
    private fun lm(x: Double, y: Double, visibility: Double = 0.99) =
        Landmark(x, y, visibility)

    private fun sideLandmarks(
        kneeAngleDeg: Double,
        trunkInclinationDeg: Double = 0.0,
        visibility: Double = 0.99,
    ): Landmarks {
        val segment = 100.0
        val bend = Math.toRadians(180.0 - kneeAngleDeg)
        val ankleX = segment * Math.sin(bend)
        val ankleY = segment + segment * Math.cos(bend)
        val shoulderX = -segment * Math.tan(Math.toRadians(trunkInclinationDeg))
        return mapOf(
            "r_shoulder" to lm(shoulderX, -segment, visibility),
            "r_hip" to lm(0.0, 0.0, visibility),
            "r_knee" to lm(0.0, segment, visibility),
            "r_ankle" to lm(ankleX, ankleY, visibility),
            "r_heel" to lm(ankleX, ankleY + 5.0, visibility),
            "r_foot" to lm(ankleX + 5.0, ankleY + 25.0, visibility),
        )
    }

    @Test
    fun `front detector preserves valgus identifier value level and message`() {
        val landmarks = mapOf(
            "l_knee" to lm(130.0, 200.0),
            "l_ankle" to lm(100.0, 300.0),
        )

        val alert = detectFront(landmarks, widthPx = 1000.0, heightPx = 1000.0).single()

        assertEquals("valgus_l", alert.metric)
        assertEquals(0.03, alert.value, 1e-9)
        assertEquals(DangerLevel.WARNING, alert.level)
        assertEquals("Left knee valgus - knees caving inward, ACL risk", alert.message)
        assertEquals("Front", alert.viewLabel)
    }

    @Test
    fun `front detector reports asymmetry and hip drop with Python identifiers`() {
        val landmarks = mapOf(
            "l_hip" to lm(100.0, 100.0),
            "l_knee" to lm(100.0, 200.0),
            "l_ankle" to lm(200.0, 200.0),
            "r_hip" to lm(200.0, 130.0),
            "r_knee" to lm(200.0, 230.0),
            "r_ankle" to lm(200.0, 330.0),
        )

        val alerts = detectFront(landmarks, widthPx = 1000.0, heightPx = 1000.0)
        val asymmetry = alerts.single { it.metric == "asymmetry" }
        val hipDrop = alerts.single { it.metric == "hip_drop" }

        // `calc_angle` adds 1e-8 to the norm product, so the raw value is
        // fractionally below 90 while Python's one-decimal message is 90.0.
        assertEquals(90.0, asymmetry.value, 1e-3)
        assertEquals(DangerLevel.DANGER, asymmetry.level)
        assertEquals("L/R asymmetry (90.0°) - imbalance risk", asymmetry.message)
        assertEquals(0.03, hipDrop.value, 1e-9)
        assertEquals(DangerLevel.WARNING, hipDrop.level)
        assertEquals("Hip drop - uneven weight distribution", hipDrop.message)
    }

    @Test
    fun `back detector preserves spine rotation and heel asymmetry behavior`() {
        val landmarks = mapOf(
            "l_shoulder" to lm(100.0, 100.0),
            "r_shoulder" to lm(200.0, 170.0),
            "l_heel" to lm(100.0, 400.0),
            "r_heel" to lm(200.0, 430.0),
        )

        val alerts = detectBack(landmarks, widthPx = 1000.0, heightPx = 1000.0)
        val rotation = alerts.single { it.metric == "spine_rot" }
        val heel = alerts.single { it.metric == "heel_asym" }

        assertEquals(0.07, rotation.value, 1e-9)
        assertEquals(DangerLevel.DANGER, rotation.level)
        assertEquals("Spine rotation - shoulders uneven", rotation.message)
        assertEquals(0.03, heel.value, 1e-9)
        assertEquals(DangerLevel.WARNING, heel.level)
        assertEquals("Heel asymmetry - one foot lifting", heel.message)
    }

    @Test
    fun `side depth alerts are gated while standing`() {
        val state = SquatStateMachine()

        val result = detectSide(sideLandmarks(kneeAngleDeg = 145.0), 1000.0, 1000.0, state)

        assertEquals(145.0, result.kneeAngleDeg!!, 1e-6)
        assertFalse(result.alerts.any { it.metric == "depth" })
    }

    @Test
    fun `side detector reports shallow and too-deep thresholds only in squat`() {
        val shallowState = SquatStateMachine().also { it.update(120.0) }
        val shallow = detectSide(sideLandmarks(kneeAngleDeg = 145.0), 1000.0, 1000.0, shallowState)
            .alerts.single { it.metric == "depth" }

        assertEquals(145.0, shallow.value, 1e-6)
        assertEquals(DangerLevel.WARNING, shallow.level)
        assertEquals("Squat too shallow - go deeper", shallow.message)

        val deepState = SquatStateMachine().also { it.update(120.0) }
        val deep = detectSide(sideLandmarks(kneeAngleDeg = 60.0), 1000.0, 1000.0, deepState)
            .alerts.single { it.metric == "depth" }

        assertEquals(60.0, deep.value, 1e-6)
        assertEquals(DangerLevel.WARNING, deep.level)
        assertEquals("Excessively deep - lumbar risk", deep.message)
    }

    @Test
    fun `side detector reports butt wink from prior trunk inclination at depth`() {
        val state = SquatStateMachine()
        state.update(120.0)
        state.update(95.0)
        assertTrue(state.isAtDepth())

        val baseline = detectSide(sideLandmarks(95.0, trunkInclinationDeg = 0.0), 1000.0, 1000.0, state)
        assertFalse(baseline.alerts.any { it.metric == "butt_wink" })

        val changed = detectSide(sideLandmarks(95.0, trunkInclinationDeg = 20.0), 1000.0, 1000.0, state)
        val buttWink = changed.alerts.single { it.metric == "butt_wink" }

        assertEquals(20.0, buttWink.value, 1e-6)
        assertEquals(DangerLevel.WARNING, buttWink.level)
        assertEquals("Butt wink - lumbar flexion at squat bottom", buttWink.message)
        assertEquals("Side", buttWink.viewLabel)
        assertNotNull(state.previousTrunkInclinationDeg)
    }

    @Test
    fun `side form alerts require an active squat but prior trunk is still sampled`() {
        val state = SquatStateMachine()
        val result = detectSide(
            sideLandmarks(kneeAngleDeg = 145.0, trunkInclinationDeg = 30.0),
            1000.0,
            1000.0,
            state,
        )

        assertTrue(result.alerts.isEmpty())
        assertEquals(30.0, state.previousTrunkInclinationDeg!!, 1e-6)
    }
}
