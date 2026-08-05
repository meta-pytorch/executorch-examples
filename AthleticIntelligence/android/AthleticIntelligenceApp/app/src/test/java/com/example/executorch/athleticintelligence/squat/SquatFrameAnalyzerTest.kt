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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (`SquatGuard._update_rep_tracking`, `_process_view`'s alert cooldown): one
 * analyzer owns the state machine, the current [RepEvidenceTracker], the
 * completed [RepRecord] list, the frame count, and the alert cooldown.
 */
class SquatFrameAnalyzerTest {

    private fun lm(x: Double, y: Double, visibility: Double = 0.99) = Landmark(x, y, visibility)

    // Builds a symmetric front-view pose whose interior knee angle (hip->knee->ankle)
    // is exactly `angleDeg`: knee sits straight below hip, ankle bends by the
    // supplementary angle so `SquatGeometry.calcAngle` returns `angleDeg` for both legs.
    private fun landmarksForAngle(angleDeg: Double): Landmarks {
        val segmentLen = 100.0
        val theta = Math.toRadians(180.0 - angleDeg)
        val kneeY = segmentLen
        val ankleX = segmentLen * Math.sin(theta)
        val ankleY = segmentLen + segmentLen * Math.cos(theta)
        return mapOf(
            "l_shoulder" to lm(-10.0, -80.0),
            "r_shoulder" to lm(10.0, -80.0),
            "l_hip" to lm(-10.0, 0.0),
            "r_hip" to lm(10.0, 0.0),
            "l_knee" to lm(-10.0, kneeY),
            "r_knee" to lm(10.0, kneeY),
            "l_ankle" to lm(ankleX - 10.0, ankleY),
            "r_ankle" to lm(ankleX + 10.0, ankleY),
        )
    }

    @Test
    fun `full rep cycle produces exactly one completed record and increments reps`() {
        val analyzer = SquatFrameAnalyzer(SquatView.FRONT)
        val angles = listOf(170.0, 155.0, 120.0, 95.0, 120.0, 155.0, 170.0)

        var lastResult: SquatFrameResult? = null
        for (angle in angles) {
            lastResult = analyzer.processFrame(landmarksForAngle(angle), 300.0, 420.0)
        }

        assertEquals(1, analyzer.reps)
        assertEquals(SquatPhase.STANDING, analyzer.phase)
        assertEquals(1, analyzer.completedRecords().size)
        assertNotNull(lastResult?.completedRep)
        assertEquals(1, lastResult?.completedRep?.repNumber)
    }

    @Test
    fun `aborted descent does not produce a completed record`() {
        val analyzer = SquatFrameAnalyzer(SquatView.FRONT)
        val angles = listOf(170.0, 145.0, 130.0, 165.0)

        var lastResult: SquatFrameResult? = null
        for (angle in angles) {
            lastResult = analyzer.processFrame(landmarksForAngle(angle), 300.0, 420.0)
        }

        assertEquals(0, analyzer.reps)
        assertEquals(SquatPhase.STANDING, analyzer.phase)
        assertTrue(analyzer.completedRecords().isEmpty())
        assertNull(lastResult?.completedRep)
    }

    @Test
    fun `missing landmarks produce zero coverage metrics without inventing a transition`() {
        val analyzer = SquatFrameAnalyzer(SquatView.FRONT)
        val startingPhase = analyzer.phase

        val result = analyzer.processFrame(emptyMap(), 300.0, 420.0)

        assertEquals(startingPhase, result.phase)
        assertEquals(SquatPhase.STANDING, analyzer.phase)
        assertNull(result.metrics.trackingKneeAngleDeg)
        assertEquals(0.0, result.metrics.visibilityCoverage, 1e-9)
        assertEquals(0, analyzer.reps)
    }

    @Test
    fun `frame count increases monotonically as frames are processed`() {
        val analyzer = SquatFrameAnalyzer(SquatView.FRONT)

        analyzer.processFrame(emptyMap(), 300.0, 420.0)
        analyzer.processFrame(emptyMap(), 300.0, 420.0)
        analyzer.processFrame(emptyMap(), 300.0, 420.0)

        assertEquals(3L, analyzer.frameCount)
    }

    @Test
    fun `cooldown applies only to immediate detector alerts while active issues stay unthrottled`() {
        val analyzer = SquatFrameAnalyzer(SquatView.FRONT, alertCooldownFrames = 3)
        // Both knees bent to ~146.6 degrees (average 146.6 < 150 -> DESCENDING),
        // left knee also caved medially past the ankle -> persistent knee_valgus_l danger.
        val valgusLandmarks = mapOf(
            "l_shoulder" to lm(-10.0, -80.0),
            "r_shoulder" to lm(10.0, -80.0),
            "l_hip" to lm(-10.0, 0.0),
            "r_hip" to lm(10.0, 0.0),
            "l_knee" to lm(20.0, 100.0),
            "r_knee" to lm(40.0, 100.0),
            "l_ankle" to lm(-10.0, 200.0),
            "r_ankle" to lm(10.0, 200.0),
        )

        val results = (1..5).map { analyzer.processFrame(valgusLandmarks, 300.0, 420.0) }

        assertTrue("expected the analyzer to enter a squat", results.all { it.phase != SquatPhase.STANDING })
        assertTrue("immediate detectors run every frame", results.all { it.immediateAlerts.any { alert -> alert.metric == "valgus_l" } })
        assertTrue("evidence active issues remain unthrottled", results.all { it.activeIssues.any { alert -> alert.metric == "knee_valgus_l" } })
        assertTrue(results[0].emittedAlerts.any { it.metric == "valgus_l" })
        assertTrue(results[1].emittedAlerts.isEmpty())
        assertTrue(results[2].emittedAlerts.isEmpty())
        assertTrue(results[3].emittedAlerts.any { it.metric == "valgus_l" })
    }

    @Test
    fun `reset clears phase reps records frame count cooldown and detector history`() {
        val analyzer = SquatFrameAnalyzer(SquatView.FRONT, alertCooldownFrames = 10)
        listOf(170.0, 155.0, 120.0, 95.0, 120.0, 155.0, 170.0).forEach {
            analyzer.processFrame(landmarksForAngle(it), 300.0, 420.0)
        }
        assertEquals(1, analyzer.reps)
        assertEquals(1, analyzer.completedRecords().size)

        val valgusLandmarks = mapOf(
            "l_shoulder" to lm(-10.0, -80.0),
            "r_shoulder" to lm(10.0, -80.0),
            "l_hip" to lm(-10.0, 0.0),
            "r_hip" to lm(10.0, 0.0),
            "l_knee" to lm(20.0, 100.0),
            "r_knee" to lm(40.0, 100.0),
            "l_ankle" to lm(-10.0, 200.0),
            "r_ankle" to lm(10.0, 200.0),
        )

        val first = analyzer.processFrame(valgusLandmarks, 300.0, 420.0)
        val suppressed = analyzer.processFrame(valgusLandmarks, 300.0, 420.0)
        assertTrue(first.emittedAlerts.isNotEmpty())
        assertTrue(suppressed.emittedAlerts.isEmpty())

        analyzer.reset()

        assertEquals(0L, analyzer.frameCount)
        assertEquals(0, analyzer.reps)
        assertEquals(SquatPhase.STANDING, analyzer.phase)
        assertTrue(analyzer.completedRecords().isEmpty())

        val afterReset = analyzer.processFrame(valgusLandmarks, 300.0, 420.0)
        assertEquals(1L, analyzer.frameCount)
        assertTrue("reset must clear alert cooldown", afterReset.emittedAlerts.isNotEmpty())
    }
}
