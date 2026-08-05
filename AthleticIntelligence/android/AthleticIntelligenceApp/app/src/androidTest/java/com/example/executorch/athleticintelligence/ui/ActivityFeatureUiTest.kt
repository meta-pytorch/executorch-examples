/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.executorch.athleticintelligence.activity.ActivityType
import com.example.executorch.athleticintelligence.activity.SessionCompletion
import com.example.executorch.athleticintelligence.history.ActivityHistoryPresentation
import com.example.executorch.athleticintelligence.history.ActivitySessionHistoryRow
import com.example.executorch.athleticintelligence.squat.SquatPhase
import com.example.executorch.athleticintelligence.squat.SquatView
import com.example.executorch.athleticintelligence.squat.TrackingStatus
import com.example.executorch.athleticintelligence.telemetry.SessionSummaryRecord
import com.example.executorch.athleticintelligence.telemetry.SquatSessionDetails
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ActivityFeatureUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun squatScreenLaunchesWithoutPickerOrChangeActivityControl() {
        setSquatContent()

        compose.onNodeWithText("Squats").assertIsDisplayed()
        compose.onNodeWithText("Form coaching").assertIsDisplayed()
        compose.onNodeWithTag("change-activity").assertDoesNotExist()
    }

    @Test
    fun frontSideAndBackViewsRemainSelectableUntilSessionStarts() {
        val selected = mutableListOf<SquatView>()
        setSquatContent(
            actions = actions(selectView = selected::add),
        )

        compose.onNodeWithText("Side").assertIsSelected()
        compose.onNodeWithText("Front").performClick()
        compose.onNodeWithText("Back").performClick()

        assertEquals(listOf(SquatView.FRONT, SquatView.BACK), selected)
    }

    @Test
    fun sessionControlAndTrackingRecoveryGuidanceAreVisible() {
        var state by mutableStateOf(baseState())
        var starts = 0
        compose.setContent {
            Box(Modifier.width(420.dp).height(900.dp)) {
                SquatFeatureScreen(
                    state = state,
                    actions = actions(start = { starts++ }),
                    cameraContent = {},
                )
            }
        }

        compose.onNodeWithContentDescription("Start Session").assertIsEnabled().performClick()
        compose.onNodeWithText("Tracking lost. Move fully into frame to resume coaching.")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(1, starts)

        state = state.copy(sessionActive = true, tracking = TrackingStatus.TRACKED, canChangeView = false)
        compose.onNodeWithContentDescription("End Session").assertIsEnabled()
        compose.onNodeWithText("Front").assertIsNotEnabled()
    }

    @Test
    fun deniedCameraShowsWorkingRecoveryAction() {
        var recoveries = 0
        setSquatContent(
            state = baseState().copy(
                backendReady = false,
                cameraStatus = "Enable camera access in Settings",
                cameraRecoveryLabel = "Open Settings",
            ),
            actions = actions(recover = { recoveries++ }),
        )

        compose.onNodeWithText("Open Settings").performScrollTo().performClick()

        assertEquals(1, recoveries)
    }

    @Test
    fun completedSquatSessionCanBeExportedFromTimeline() {
        val history = ActivityHistoryPresentation(
            activityType = ActivityType.SQUATS,
            sessionRows = listOf(
                ActivitySessionHistoryRow(
                    sessionId = "session-1",
                    title = "Squat session",
                    detail = "3 reps • 88 average score",
                    startedAtUtc = "2026-07-12T11:00:00Z",
                    startedAtEpochMs = 1_752_317_200_000L,
                    actionCount = 3,
                    status = "Complete",
                ),
            ),
            mistakeRows = emptyList(),
            totalActions = 3,
            averageScore = 88,
        )
        val summary = SessionSummaryRecord(
            sessionId = "session-1",
            activityType = ActivityType.SQUATS,
            telemetryFileName = "session-1.jsonl",
            completion = SessionCompletion.COMPLETE,
            frameCount = 100,
            startedAtUtc = "2026-07-12T11:00:00Z",
            endedAtUtc = "2026-07-12T11:01:00Z",
            details = SquatSessionDetails("side", "session-1.mp4"),
        )
        val exported = mutableListOf<String>()
        setSquatContent(
            state = baseState().copy(
                selectedDestination = DashboardDestination.TIMELINE,
                history = history,
                summaries = listOf(summary),
            ),
            actions = actions(export = exported::add),
        )

        compose.onNodeWithText("Squat session").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Export").performScrollTo().performClick()

        assertEquals(listOf("session-1"), exported)
    }

    private fun setSquatContent(
        state: SquatFeatureUiState = baseState(),
        actions: SquatFeatureActions = actions(),
    ) {
        compose.setContent {
            Box(Modifier.width(420.dp).height(900.dp)) {
                SquatFeatureScreen(state, actions, cameraContent = {})
            }
        }
    }

    private fun baseState() = SquatFeatureUiState(
        selectedDestination = DashboardDestination.HOME,
        timelineSegment = TimelineSegment.SESSION,
        backendReady = true,
        sessionActive = false,
        phase = SquatPhase.STANDING,
        repCount = 0,
        currentCorrection = null,
        latestScore = null,
        tracking = TrackingStatus.PERSON_NOT_DETECTED,
        history = ActivityHistoryPresentation(ActivityType.SQUATS, emptyList(), emptyList(), 0, null),
        selectedView = SquatView.SIDE,
    )

    private fun actions(
        start: () -> Unit = {},
        recover: () -> Unit = {},
        selectView: (SquatView) -> Unit = {},
        export: (String) -> Unit = {},
    ) = SquatFeatureActions(
        selectDestination = {},
        selectTimelineSegment = {},
        startSession = start,
        endSession = {},
        recoverCamera = recover,
        selectView = selectView,
        exportSession = export,
    )
}
