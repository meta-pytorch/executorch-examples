/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

/**
 * ~180deg = straight leg (standing), ~90deg = deep squat. Interior knee angle:
 * ~170-180deg standing, ~90deg at parallel squat depth.
 */
class SquatStateMachine {
    companion object {
        /** Mirrors `STANDING_MIN`: above this = standing (nearly straight legs). */
        const val STANDING_MIN = 160.0

        /** Mirrors `DESCENT_THRESH`: below this = descending (legs starting to bend). */
        const val DESCENT_THRESH = 150.0

        /** Mirrors `DEPTH_THRESH`: below this = at depth (deep squat). */
        const val DEPTH_THRESH = 110.0
    }

    var phase: SquatPhase = SquatPhase.STANDING
        private set
    var reps: Int = 0
        private set

    /**
     * Mirrors Python's `_prev_trunk_incl`. The side detector samples this on
     * every frame and compares it only while [isAtDepth] for butt-wink alerts.
     */
    internal var previousTrunkInclinationDeg: Double? = null

    /** Mirrors `SquatStateMachine.update`. Returns the (possibly unchanged) phase. A null [kneeAngleDeg] is a no-op. */
    fun update(kneeAngleDeg: Double?): SquatPhase {
        if (kneeAngleDeg == null) return phase
        when (phase) {
            SquatPhase.STANDING -> {
                if (kneeAngleDeg < DESCENT_THRESH) {
                    phase = SquatPhase.DESCENDING
                }
            }
            SquatPhase.DESCENDING -> {
                if (kneeAngleDeg < DEPTH_THRESH) {
                    phase = SquatPhase.AT_DEPTH
                } else if (kneeAngleDeg >= STANDING_MIN) {
                    // Stood back up without reaching depth (aborted rep).
                    phase = SquatPhase.STANDING
                }
            }
            SquatPhase.AT_DEPTH -> {
                if (kneeAngleDeg >= DEPTH_THRESH) {
                    phase = SquatPhase.ASCENDING
                }
            }
            SquatPhase.ASCENDING -> {
                if (kneeAngleDeg >= STANDING_MIN) {
                    // Fully stood up - rep complete!
                    phase = SquatPhase.STANDING
                    reps += 1
                } else if (kneeAngleDeg < DEPTH_THRESH) {
                    // Went back down before fully standing.
                    phase = SquatPhase.AT_DEPTH
                }
            }
        }
        return phase
    }

    fun isAtDepth(): Boolean = phase == SquatPhase.AT_DEPTH

    fun isInSquat(): Boolean = phase != SquatPhase.STANDING

    fun reset() {
        phase = SquatPhase.STANDING
        reps = 0
        previousTrunkInclinationDeg = null
    }
}
