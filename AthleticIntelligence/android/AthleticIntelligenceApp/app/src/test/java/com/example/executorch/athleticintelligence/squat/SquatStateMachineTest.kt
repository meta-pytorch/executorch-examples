/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Port of `tests/test_squat_core_scoring.py::SquatStateMachineTests` plus two
 * additional fixtures (abort path, null-preserves-phase) exercising the same
 */
class SquatStateMachineTest {

    @Test
    fun `full descend and ascend cycle completes exactly one rep`() {
        val sm = SquatStateMachine()
        val angles = listOf(170.0, 155.0, 120.0, 95.0, 120.0, 155.0, 170.0)

        val phases = angles.map { sm.update(it) }

        assertEquals(
            listOf(
                SquatPhase.STANDING,
                SquatPhase.STANDING,
                SquatPhase.DESCENDING,
                SquatPhase.AT_DEPTH,
                SquatPhase.ASCENDING,
                SquatPhase.ASCENDING,
                SquatPhase.STANDING,
            ),
            phases,
        )
        assertEquals(1, sm.reps)
        assertEquals(SquatPhase.STANDING, sm.phase)
    }

    @Test
    fun `descent that stands back up before reaching depth aborts without completing a rep`() {
        val sm = SquatStateMachine()
        val angles = listOf(170.0, 145.0, 130.0, 165.0)

        val phases = angles.map { sm.update(it) }

        assertEquals(
            listOf(
                SquatPhase.STANDING,
                SquatPhase.DESCENDING,
                SquatPhase.DESCENDING,
                SquatPhase.STANDING,
            ),
            phases,
        )
        assertEquals(0, sm.reps)
        assertEquals(SquatPhase.STANDING, sm.phase)
    }

    @Test
    fun `null knee angle preserves the current phase without side effects`() {
        val sm = SquatStateMachine()
        sm.update(120.0)
        assertEquals(SquatPhase.DESCENDING, sm.phase)

        val phase = sm.update(null)

        assertEquals(SquatPhase.DESCENDING, phase)
        assertEquals(SquatPhase.DESCENDING, sm.phase)
        assertEquals(0, sm.reps)
    }

    @Test
    fun `is at depth and is in squat reflect the current phase`() {
        val sm = SquatStateMachine()
        assertEquals(false, sm.isInSquat())
        assertEquals(false, sm.isAtDepth())

        sm.update(120.0)
        assertEquals(true, sm.isInSquat())
        assertEquals(false, sm.isAtDepth())

        sm.update(95.0)
        assertEquals(true, sm.isInSquat())
        assertEquals(true, sm.isAtDepth())
    }

    @Test
    fun `reset returns the machine to standing with zero reps`() {
        val sm = SquatStateMachine()
        listOf(170.0, 155.0, 120.0, 95.0, 120.0, 155.0, 170.0).forEach { sm.update(it) }
        assertEquals(1, sm.reps)
        sm.previousTrunkInclinationDeg = 18.0

        sm.reset()

        assertEquals(SquatPhase.STANDING, sm.phase)
        assertEquals(0, sm.reps)
        assertEquals(null, sm.previousTrunkInclinationDeg)
    }
}
