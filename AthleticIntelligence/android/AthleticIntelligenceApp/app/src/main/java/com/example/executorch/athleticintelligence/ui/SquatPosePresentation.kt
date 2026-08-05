/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.ui

import com.example.executorch.athleticintelligence.activity.SquatActivityFrameResult
import com.example.executorch.athleticintelligence.squat.Landmarks
import com.example.executorch.athleticintelligence.squat.SquatAnnotationFrame
import com.example.executorch.athleticintelligence.squat.toAnnotationFrame
import kotlin.math.max

data class SquatPoseUiState(
    val annotationFrame: SquatAnnotationFrame,
) {
    val landmarks: Landmarks get() = annotationFrame.landmarks
    val sourceWidthPx: Int get() = annotationFrame.sourceWidthPx
    val sourceHeightPx: Int get() = annotationFrame.sourceHeightPx
}

private val squatOverlayLandmarkNames = setOf(
    "l_shoulder",
    "r_shoulder",
    "l_hip",
    "r_hip",
    "l_knee",
    "r_knee",
    "l_ankle",
    "r_ankle",
)

internal val squatOverlayConnections = listOf(
    "l_shoulder" to "r_shoulder",
    "l_shoulder" to "l_hip",
    "r_shoulder" to "r_hip",
    "l_hip" to "r_hip",
    "l_hip" to "l_knee",
    "r_hip" to "r_knee",
    "l_knee" to "l_ankle",
    "r_knee" to "r_ankle",
)

internal fun squatOverlayLandmarks(
    landmarks: Landmarks,
    minimumVisibility: Double = .2,
): Landmarks = landmarks.filter { (name, landmark) ->
    name in squatOverlayLandmarkNames && landmark.visibility >= minimumVisibility
}

data class ProjectedPosePoint(
    val x: Float,
    val y: Float,
)

data class CenterCropPoseProjection(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    fun project(xPx: Double, yPx: Double): ProjectedPosePoint =
        ProjectedPosePoint(
            x = offsetX + xPx.toFloat() * scale,
            y = offsetY + yPx.toFloat() * scale,
        )
}

fun centerCropPoseProjection(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    targetWidthPx: Float,
    targetHeightPx: Float,
): CenterCropPoseProjection {
    require(sourceWidthPx > 0 && sourceHeightPx > 0) { "Pose source dimensions must be positive" }
    require(targetWidthPx > 0f && targetHeightPx > 0f) { "Pose target dimensions must be positive" }
    val scale = max(
        targetWidthPx / sourceWidthPx.toFloat(),
        targetHeightPx / sourceHeightPx.toFloat(),
    )
    return CenterCropPoseProjection(
        scale = scale,
        offsetX = (targetWidthPx - sourceWidthPx * scale) / 2f,
        offsetY = (targetHeightPx - sourceHeightPx * scale) / 2f,
    )
}

fun SquatActivityFrameResult.toPoseUiState(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
): SquatPoseUiState {
    require(sourceWidthPx > 0 && sourceHeightPx > 0) { "Pose source dimensions must be positive" }
    return SquatPoseUiState(toAnnotationFrame(sourceWidthPx, sourceHeightPx))
}
