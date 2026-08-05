/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Direct port of `tests/test_squat_core_scoring.py::RepEvidenceTrackerTests`.
 * (see `MetricRule`, `METRIC_RULES`, `VIEW_METRICS`, and `RepEvidenceTracker`).
 */
class RepEvidenceTrackerTest {

    /** Mirrors the Python test helper `frame(view, phase, tracking_angle=100.0, visibility=1.0, **values)`. */
    private fun frame(
        view: SquatView,
        phase: SquatPhase,
        trackingAngle: Double? = 100.0,
        visibility: Double = 1.0,
        values: Map<String, Double> = emptyMap(),
    ): FrameMetrics {
        val availability = values.keys.associateWith { MetricAvailability.AVAILABLE }
        return FrameMetrics(
            view = view,
            phase = phase,
            trackingKneeAngleDeg = trackingAngle,
            values = values,
            availability = availability,
            visibilityCoverage = visibility,
        )
    }

    @Test
    fun `clean front rep grades good`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.FRONT)
        repeat(12) {
            tracker.recordFrame(
                frame(
                    SquatView.FRONT,
                    SquatPhase.DESCENDING,
                    values = mapOf("knee_valgus_l" to 0.0, "knee_valgus_r" to 0.0, "hip_drop" to 0.0, "asymmetry" to 2.0),
                ),
            )
        }

        val record = tracker.finalize()

        assertEquals(RepGrade.GOOD, record.grade)
        assertTrue(record.score >= 80)
        assertEquals("Good form", record.primaryCorrection)
    }

    @Test
    fun `COCO unavailable side foot metrics remain not applicable in rep summary`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.SIDE)
        tracker.recordFrame(
            FrameMetrics(
                view = SquatView.SIDE,
                phase = SquatPhase.DESCENDING,
                trackingKneeAngleDeg = 100.0,
                values = mapOf("depth_angle" to 100.0, "trunk_tibia" to 0.0),
                availability = mapOf(
                    "depth_angle" to MetricAvailability.AVAILABLE,
                    "trunk_tibia" to MetricAvailability.AVAILABLE,
                    "heel_lift" to MetricAvailability.NOT_APPLICABLE,
                    "knee_over_toes" to MetricAvailability.NOT_APPLICABLE,
                ),
                visibilityCoverage = 1.0,
            ),
        )

        val record = tracker.finalize()

        assertEquals(
            MetricAvailability.NOT_APPLICABLE,
            record.metricSummaries.getValue("heel_lift").status,
        )
        assertEquals(
            MetricAvailability.NOT_APPLICABLE,
            record.metricSummaries.getValue("knee_over_toes").status,
        )
    }

    @Test
    fun `persistent front valgus grades bad and selects the valgus message`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.FRONT)
        repeat(12) {
            tracker.recordFrame(
                frame(
                    SquatView.FRONT,
                    SquatPhase.DESCENDING,
                    values = mapOf("knee_valgus_l" to 0.08, "knee_valgus_r" to 0.0, "hip_drop" to 0.0, "asymmetry" to 2.0),
                ),
            )
        }

        val record = tracker.finalize()

        assertEquals(RepGrade.BAD, record.grade)
        assertTrue(record.score < 55)
        assertTrue(record.primaryCorrection.lowercase().contains("knee"))
    }

    @Test
    fun `one frame valgus spike does not dominate the grade`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.FRONT)
        for (i in 0 until 20) {
            val valgus = if (i == 10) 0.08 else 0.0
            tracker.recordFrame(
                frame(
                    SquatView.FRONT,
                    SquatPhase.DESCENDING,
                    values = mapOf("knee_valgus_l" to valgus, "knee_valgus_r" to 0.0, "hip_drop" to 0.0, "asymmetry" to 2.0),
                ),
            )
        }

        val record = tracker.finalize()

        assertEquals(RepGrade.GOOD, record.grade)
        assertTrue(record.score >= 80)
    }

    @Test
    fun `poor visibility grades bad with a visibility message`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.FRONT)
        repeat(10) {
            tracker.recordFrame(
                frame(
                    SquatView.FRONT,
                    SquatPhase.DESCENDING,
                    visibility = 0.2,
                    values = mapOf("knee_valgus_l" to 0.0),
                ),
            )
        }

        val record = tracker.finalize()

        assertEquals(RepGrade.BAD, record.grade)
        assertTrue(record.primaryCorrection.lowercase().contains("visibility"))
    }

    @Test
    fun `mixed issues use the highest evidence contribution as primary correction`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.FRONT)
        repeat(12) {
            tracker.recordFrame(
                frame(
                    SquatView.FRONT,
                    SquatPhase.DESCENDING,
                    values = mapOf("knee_valgus_l" to 0.07, "hip_drop" to 0.03, "asymmetry" to 3.0),
                ),
            )
        }

        val record = tracker.finalize()

        assertTrue(record.primaryCorrection.lowercase().contains("knee"))
        assertTrue(record.secondaryCorrections.isNotEmpty())
    }

    @Test
    fun `clean side rep grades good`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.SIDE)
        for (angle in listOf(145.0, 130.0, 105.0, 90.0, 105.0, 130.0, 145.0, 160.0)) {
            tracker.recordFrame(
                frame(
                    SquatView.SIDE,
                    SquatPhase.DESCENDING,
                    trackingAngle = angle,
                    values = mapOf("depth_angle" to angle, "trunk_tibia" to 0.0, "heel_lift" to 0.0, "knee_over_toes" to 0.0),
                ),
            )
        }

        val record = tracker.finalize()

        assertEquals(RepGrade.GOOD, record.grade)
        assertEquals("Good form", record.primaryCorrection)
        assertEquals(0.0, record.metricSummaries.getValue("depth_angle").contribution, 1e-9)
    }

    @Test
    fun `side depth scoring uses the bottom angle not transition frames`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.SIDE)
        for (angle in listOf(145.0, 130.0, 105.0, 90.0, 105.0, 130.0, 145.0, 160.0)) {
            tracker.recordFrame(
                frame(SquatView.SIDE, SquatPhase.DESCENDING, trackingAngle = angle, values = mapOf("depth_angle" to angle)),
            )
        }

        val record = tracker.finalize()

        assertEquals(RepGrade.GOOD, record.grade)
        assertNotEquals("Squat deeper with control", record.primaryCorrection)
        assertEquals(0.0, record.metricSummaries.getValue("depth_angle").contribution, 1e-9)
    }

    @Test
    fun `shallow side rep gets the depth correction`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.SIDE)
        for (angle in listOf(160.0, 152.0, 148.0, 145.0, 148.0, 152.0, 160.0)) {
            tracker.recordFrame(
                frame(SquatView.SIDE, SquatPhase.DESCENDING, trackingAngle = angle, values = mapOf("depth_angle" to angle)),
            )
        }

        val record = tracker.finalize()

        assertNotEquals(RepGrade.GOOD, record.grade)
        assertEquals("Squat deeper with control", record.primaryCorrection)
        assertTrue(record.metricSummaries.getValue("depth_angle").contribution > 0.0)
    }

    @Test
    fun `negative trunk tibia flags knee-dominant loading`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.SIDE)
        repeat(12) {
            tracker.recordFrame(
                frame(
                    SquatView.SIDE,
                    SquatPhase.DESCENDING,
                    trackingAngle = 95.0,
                    values = mapOf("depth_angle" to 95.0, "trunk_tibia" to -15.0, "heel_lift" to 0.0, "knee_over_toes" to 0.0),
                ),
            )
        }

        val record = tracker.finalize()

        assertNotEquals(RepGrade.GOOD, record.grade)
        assertTrue(record.primaryCorrection.lowercase().contains("knee-dominant"))
        assertTrue(record.metricSummaries.getValue("trunk_tibia").contribution > 0.0)
    }

    @Test
    fun `positive trunk tibia still flags forward lean`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.SIDE)
        repeat(12) {
            tracker.recordFrame(
                frame(
                    SquatView.SIDE,
                    SquatPhase.DESCENDING,
                    trackingAngle = 95.0,
                    values = mapOf("depth_angle" to 95.0, "trunk_tibia" to 15.0, "heel_lift" to 0.0, "knee_over_toes" to 0.0),
                ),
            )
        }

        val record = tracker.finalize()

        assertNotEquals(RepGrade.GOOD, record.grade)
        assertTrue(record.primaryCorrection.lowercase().contains("torso"))
        assertTrue(record.metricSummaries.getValue("trunk_tibia").contribution > 0.0)
    }

    @Test
    fun `expected side metric without samples exports insufficient data`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.SIDE)
        for (angle in listOf(130.0, 100.0, 130.0)) {
            tracker.recordFrame(
                frame(SquatView.SIDE, SquatPhase.DESCENDING, trackingAngle = angle, values = mapOf("depth_angle" to angle)),
            )
        }

        val record = tracker.finalize()
        val summary = record.metricSummaries.getValue("trunk_tibia")

        assertEquals(MetricAvailability.INSUFFICIENT_DATA, summary.status)
        assertEquals(0, summary.validFrames)
        assertEquals(3, summary.totalFrames)
        assertEquals(0.0, summary.contribution, 1e-9)
    }

    @Test
    fun `front rep reports side-only metrics as not applicable`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.FRONT)
        repeat(12) {
            tracker.recordFrame(
                frame(
                    SquatView.FRONT,
                    SquatPhase.DESCENDING,
                    values = mapOf("knee_valgus_l" to 0.0, "knee_valgus_r" to 0.0, "hip_drop" to 0.0, "asymmetry" to 2.0),
                ),
            )
        }

        val record = tracker.finalize()

        assertEquals(100, record.score)
        assertEquals(MetricAvailability.NOT_APPLICABLE, record.metricSummaries.getValue("depth_angle").status)
        assertEquals(MetricAvailability.NOT_APPLICABLE, record.metricSummaries.getValue("heel_lift").status)
        assertEquals(0.0, record.metricSummaries.getValue("depth_angle").contribution, 1e-9)
        assertEquals(0.0, record.metricSummaries.getValue("heel_lift").contribution, 1e-9)
        assertEquals(MetricAvailability.AVAILABLE, record.metricSummaries.getValue("knee_valgus_l").status)
        assertEquals(0.0, record.metricSummaries.getValue("knee_valgus_l").contribution, 1e-9)
    }

    @Test
    fun `empty tracker grades bad with zero score and 180 degree minimum angle`() {
        val tracker = RepEvidenceTracker(repNumber = 1, view = SquatView.FRONT)

        val record = tracker.finalize()

        assertEquals(0, record.score)
        assertEquals(RepGrade.BAD, record.grade)
        assertEquals(180.0, record.minKneeAngleDeg, 1e-9)
    }
}
