/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.ui

import com.example.executorch.athleticintelligence.activity.SquatActivityFrameResult
import com.example.executorch.athleticintelligence.squat.Landmark
import com.example.executorch.athleticintelligence.squat.SquatPhase
import com.example.executorch.athleticintelligence.squat.TrackingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SquatPosePresentationTest {
    @Test
    fun `frame result landmarks become explicitly sized pose presentation`() {
        val landmarks = mapOf("l_knee" to Landmark(12.0, 34.0, .9))
        val result = SquatActivityFrameResult(
            phase = SquatPhase.DESCENDING,
            repCount = 1,
            currentCorrection = null,
            latestScore = null,
            landmarks = landmarks,
            tracking = TrackingStatus.TRACKED,
        )

        val pose = result.toPoseUiState(sourceWidthPx = 200, sourceHeightPx = 300)

        assertEquals(landmarks, pose.landmarks)
        assertEquals(200, pose.sourceWidthPx)
        assertEquals(300, pose.sourceHeightPx)
    }

    @Test
    fun `pose presentation rejects unknown source dimensions`() {
        val result = SquatActivityFrameResult(
            SquatPhase.STANDING,
            0,
            null,
            null,
            emptyMap(),
            TrackingStatus.PERSON_NOT_DETECTED,
        )

        assertThrows(IllegalArgumentException::class.java) {
            result.toPoseUiState(sourceWidthPx = 0, sourceHeightPx = 300)
        }
    }

    @Test
    fun `center crop projection matches fill center for mismatched aspect ratios`() {
        val projection = centerCropPoseProjection(
            sourceWidthPx = 400,
            sourceHeightPx = 200,
            targetWidthPx = 300f,
            targetHeightPx = 300f,
        )

        assertEquals(1.5f, projection.scale, 0f)
        assertEquals(-150f, projection.offsetX, 0f)
        assertEquals(0f, projection.offsetY, 0f)
        assertEquals(
            ProjectedPosePoint(x = 150f, y = 150f),
            projection.project(xPx = 200.0, yPx = 100.0),
        )
        assertEquals(
            ProjectedPosePoint(x = -150f, y = 0f),
            projection.project(xPx = 0.0, yPx = 0.0),
        )
    }

    @Test
    fun `squat overlay keeps only form-relevant joints and connections`() {
        val landmark = Landmark(10.0, 20.0, .9)
        val visible = squatOverlayLandmarks(
            mapOf(
                "nose" to landmark,
                "l_shoulder" to landmark,
                "r_shoulder" to landmark,
                "l_elbow" to landmark,
                "l_wrist" to landmark,
                "l_hip" to landmark,
                "r_hip" to landmark,
                "l_knee" to landmark,
                "r_knee" to landmark,
                "l_ankle" to landmark,
                "r_ankle" to landmark,
            ),
        )

        assertEquals(
            setOf(
                "l_shoulder",
                "r_shoulder",
                "l_hip",
                "r_hip",
                "l_knee",
                "r_knee",
                "l_ankle",
                "r_ankle",
            ),
            visible.keys,
        )
        assertEquals(
            listOf(
                "l_shoulder" to "r_shoulder",
                "l_shoulder" to "l_hip",
                "r_shoulder" to "r_hip",
                "l_hip" to "r_hip",
                "l_hip" to "l_knee",
                "r_hip" to "r_knee",
                "l_knee" to "l_ankle",
                "r_knee" to "r_ankle",
            ),
            squatOverlayConnections,
        )
    }
}
