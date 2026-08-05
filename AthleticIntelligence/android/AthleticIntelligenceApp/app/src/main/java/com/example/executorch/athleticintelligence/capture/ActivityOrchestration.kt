/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.capture

import com.example.executorch.athleticintelligence.ui.DashboardDestination

enum class CameraReadiness {
    NEEDS_PERMISSION,
    PERMISSION_DENIED,
    PERMISSION_PERMANENTLY_DENIED,
    BINDING,
    READY,
    ERROR,
}

enum class CameraRecoveryAction {
    NONE,
    REQUEST_PERMISSION,
    OPEN_SETTINGS,
    RETRY_BINDING,
}

enum class SquatSessionLifecycle {
    IDLE,
    ACTIVE,
    ENDING,
}

data class SquatOrchestrationState(
    val generation: Long = 0L,
    val destination: DashboardDestination = DashboardDestination.HOME,
    val backendReady: Boolean = false,
    val cameraReadiness: CameraReadiness = CameraReadiness.NEEDS_PERMISSION,
    val sessionLifecycle: SquatSessionLifecycle = SquatSessionLifecycle.IDLE,
) {
    val canStartSquat: Boolean
        get() = backendReady &&
            cameraReadiness == CameraReadiness.READY &&
            sessionLifecycle == SquatSessionLifecycle.IDLE

    val shouldAnalyzeFrames: Boolean
        get() = backendReady &&
            destination == DashboardDestination.HOME &&
            cameraReadiness == CameraReadiness.READY

    val cameraRecoveryAction: CameraRecoveryAction
        get() = when (cameraReadiness) {
            CameraReadiness.NEEDS_PERMISSION,
            CameraReadiness.PERMISSION_DENIED -> CameraRecoveryAction.REQUEST_PERMISSION
            CameraReadiness.PERMISSION_PERMANENTLY_DENIED -> CameraRecoveryAction.OPEN_SETTINGS
            CameraReadiness.ERROR -> CameraRecoveryAction.RETRY_BINDING
            CameraReadiness.BINDING,
            CameraReadiness.READY -> CameraRecoveryAction.NONE
        }

    val cameraStatusText: String?
        get() = when (cameraReadiness) {
            CameraReadiness.NEEDS_PERMISSION -> "Camera access is required to start"
            CameraReadiness.PERMISSION_DENIED -> "Camera access was denied"
            CameraReadiness.PERMISSION_PERMANENTLY_DENIED -> "Enable camera access in Settings"
            CameraReadiness.BINDING -> "Preparing camera"
            CameraReadiness.READY -> null
            CameraReadiness.ERROR -> "Camera unavailable"
        }

    fun acceptsGeneration(candidate: Long): Boolean = candidate == generation
}

sealed interface SquatOrchestrationEvent {
    data class Started(val cameraPermissionGranted: Boolean) : SquatOrchestrationEvent
    data object BackendReady : SquatOrchestrationEvent
    data class DestinationSelected(val destination: DashboardDestination) : SquatOrchestrationEvent
    data object CameraBindingStarted : SquatOrchestrationEvent
    data object CameraBound : SquatOrchestrationEvent
    data object CameraBindingFailed : SquatOrchestrationEvent
    data class CameraPermissionDenied(val permanently: Boolean) : SquatOrchestrationEvent
    data object SessionStarted : SquatOrchestrationEvent
    data object SessionEnding : SquatOrchestrationEvent
    data object SessionEnded : SquatOrchestrationEvent
}

fun reduceSquatOrchestration(
    state: SquatOrchestrationState,
    event: SquatOrchestrationEvent,
): SquatOrchestrationState = when (event) {
    is SquatOrchestrationEvent.Started -> SquatOrchestrationState(
        generation = state.generation + 1L,
        cameraReadiness = if (event.cameraPermissionGranted) {
            CameraReadiness.BINDING
        } else {
            CameraReadiness.NEEDS_PERMISSION
        },
    )
    SquatOrchestrationEvent.BackendReady -> state.copy(backendReady = true)
    is SquatOrchestrationEvent.DestinationSelected -> state.copy(destination = event.destination)
    SquatOrchestrationEvent.CameraBindingStarted -> state.copy(cameraReadiness = CameraReadiness.BINDING)
    SquatOrchestrationEvent.CameraBound -> state.copy(cameraReadiness = CameraReadiness.READY)
    SquatOrchestrationEvent.CameraBindingFailed -> state.copy(cameraReadiness = CameraReadiness.ERROR)
    is SquatOrchestrationEvent.CameraPermissionDenied -> state.copy(
        cameraReadiness = if (event.permanently) {
            CameraReadiness.PERMISSION_PERMANENTLY_DENIED
        } else {
            CameraReadiness.PERMISSION_DENIED
        },
    )
    SquatOrchestrationEvent.SessionStarted ->
        state.copy(sessionLifecycle = SquatSessionLifecycle.ACTIVE)
    SquatOrchestrationEvent.SessionEnding ->
        state.copy(sessionLifecycle = SquatSessionLifecycle.ENDING)
    SquatOrchestrationEvent.SessionEnded ->
        state.copy(sessionLifecycle = SquatSessionLifecycle.IDLE)
}
