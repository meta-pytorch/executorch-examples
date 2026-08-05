/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.capture

import com.example.executorch.athleticintelligence.ui.DashboardDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityOrchestrationTest {
    @Test
    fun `squat start requires an actually bound camera`() {
        var state = SquatOrchestrationState()
        state = reduceSquatOrchestration(
            state,
            SquatOrchestrationEvent.Started(cameraPermissionGranted = false),
        )
        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.BackendReady)

        assertFalse(state.canStartSquat)
        assertEquals(CameraReadiness.NEEDS_PERMISSION, state.cameraReadiness)
        assertEquals(CameraRecoveryAction.REQUEST_PERMISSION, state.cameraRecoveryAction)

        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.CameraBindingStarted)
        assertFalse(state.canStartSquat)
        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.CameraBound)

        assertTrue(state.canStartSquat)
    }

    @Test
    fun `squat cannot restart while prior session drains`() {
        var state = activeSquatState()
        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.SessionStarted)
        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.SessionEnding)

        assertEquals(SquatSessionLifecycle.ENDING, state.sessionLifecycle)
        assertFalse(state.canStartSquat)

        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.SessionEnded)
        assertEquals(SquatSessionLifecycle.IDLE, state.sessionLifecycle)
        assertTrue(state.canStartSquat)
    }

    @Test
    fun `camera denial exposes retry settings and bind recovery`() {
        var state = activeSquatState()
        state = reduceSquatOrchestration(
            state,
            SquatOrchestrationEvent.CameraPermissionDenied(permanently = true),
        )
        assertEquals(CameraRecoveryAction.OPEN_SETTINGS, state.cameraRecoveryAction)

        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.CameraBindingFailed)
        assertEquals(CameraRecoveryAction.RETRY_BINDING, state.cameraRecoveryAction)
    }

    @Test
    fun `restart resets navigation and invalidates stale work`() {
        var state = activeSquatState()
        val oldGeneration = state.generation
        state = reduceSquatOrchestration(
            state,
            SquatOrchestrationEvent.DestinationSelected(DashboardDestination.INSIGHTS),
        )
        assertFalse(state.shouldAnalyzeFrames)

        state = reduceSquatOrchestration(
            state,
            SquatOrchestrationEvent.Started(cameraPermissionGranted = true),
        )

        assertEquals(DashboardDestination.HOME, state.destination)
        assertTrue(state.generation > oldGeneration)
        assertFalse(state.acceptsGeneration(oldGeneration))
    }

    @Test
    fun `model readiness and camera readiness are both required`() {
        var state = reduceSquatOrchestration(
            SquatOrchestrationState(),
            SquatOrchestrationEvent.Started(cameraPermissionGranted = true),
        )
        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.CameraBound)
        assertFalse(state.canStartSquat)

        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.BackendReady)
        assertTrue(state.canStartSquat)
    }

    private fun activeSquatState(): SquatOrchestrationState {
        var state = reduceSquatOrchestration(
            SquatOrchestrationState(),
            SquatOrchestrationEvent.Started(cameraPermissionGranted = true),
        )
        state = reduceSquatOrchestration(state, SquatOrchestrationEvent.BackendReady)
        return reduceSquatOrchestration(state, SquatOrchestrationEvent.CameraBound)
    }
}
