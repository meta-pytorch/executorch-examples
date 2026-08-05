/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import com.example.executorch.athleticintelligence.activity.SquatActivityFrameResult
import kotlin.math.max
import kotlin.math.min

data class SquatAnnotationFrame(
    val landmarks: Landmarks,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val view: SquatView?,
    val tracking: TrackingStatus,
    val phase: SquatPhase,
    val repCount: Int,
    val latestScore: Int?,
    val currentCorrection: String?,
    val immediateAlerts: List<Alert> = emptyList(),
    val activeIssues: List<Alert> = emptyList(),
    val angleAnnotations: List<SquatAngleAnnotation> = emptyList(),
)

/** One angle to annotate on the squat overlay: value drawn at [vertex], with an arc
 *  swept between the two bones vertex->a and vertex->c. Named by landmark key
 *  (e.g. "l_knee") rather than numeric keypoint index, matching [Landmarks]. */
data class SquatAngleAnnotation(
    val vertex: String,
    val a: String,
    val c: String,
    val degrees: Double,
    val label: String,
)

/**
 * Hip (shoulder-hip-knee) and knee (hip-knee-ankle) angles, reusing
 * [SquatGeometry.calcAngle] — the same math that grades squat depth — rather
 * than recomputing angle logic for display. For [SquatView.SIDE], only the
 * more-visible side is annotated (mirroring [SquatGeometry.trackingKneeAngle]'s
 * side preference) since left/right nearly coincide on screen from the side
 * and would otherwise overlap; front/back show both sides.
 */
fun squatAngleAnnotations(landmarks: Landmarks, view: SquatView? = null): List<SquatAngleAnnotation> {
    val sides = if (view == SquatView.SIDE) {
        val rVis = SquatGeometry.visibilitySum(landmarks, listOf("r_hip", "r_knee", "r_ankle"))
        val lVis = SquatGeometry.visibilitySum(landmarks, listOf("l_hip", "l_knee", "l_ankle"))
        listOf(if (rVis >= lVis) "r" else "l")
    } else {
        listOf("l", "r")
    }
    val annotations = mutableListOf<SquatAngleAnnotation>()
    for (side in sides) {
        val shoulder = SquatGeometry.validXy(landmarks, "${side}_shoulder")
        val hip = SquatGeometry.validXy(landmarks, "${side}_hip")
        val knee = SquatGeometry.validXy(landmarks, "${side}_knee")
        val ankle = SquatGeometry.validXy(landmarks, "${side}_ankle")
        if (shoulder != null && hip != null && knee != null) {
            annotations += SquatAngleAnnotation(
                vertex = "${side}_hip",
                a = "${side}_shoulder",
                c = "${side}_knee",
                degrees = SquatGeometry.calcAngle(shoulder, hip, knee),
                label = "hip",
            )
        }
        if (hip != null && knee != null && ankle != null) {
            annotations += SquatAngleAnnotation(
                vertex = "${side}_knee",
                a = "${side}_hip",
                c = "${side}_ankle",
                degrees = SquatGeometry.calcAngle(hip, knee, ankle),
                label = "knee",
            )
        }
    }
    return annotations
}

enum class SquatOverlayTone {
    SUCCESS,
    WARNING,
    ERROR,
}

fun squatAlertTone(level: DangerLevel): SquatOverlayTone = when (level) {
    DangerLevel.SAFE -> SquatOverlayTone.SUCCESS
    DangerLevel.WARNING -> SquatOverlayTone.WARNING
    DangerLevel.DANGER -> SquatOverlayTone.ERROR
}

val squatAnnotationLandmarkNames = setOf(
    "l_shoulder",
    "r_shoulder",
    "l_hip",
    "r_hip",
    "l_knee",
    "r_knee",
    "l_ankle",
    "r_ankle",
)

val squatAnnotationConnections = listOf(
    "l_shoulder" to "r_shoulder",
    "l_shoulder" to "l_hip",
    "r_shoulder" to "r_hip",
    "l_hip" to "r_hip",
    "l_hip" to "l_knee",
    "r_hip" to "r_knee",
    "l_knee" to "l_ankle",
    "r_knee" to "r_ankle",
)

fun visibleSquatLandmarks(
    landmarks: Landmarks,
    minimumVisibility: Double = .2,
): Landmarks = landmarks.filter { (name, landmark) ->
    name in squatAnnotationLandmarkNames && landmark.visibility >= minimumVisibility
}

data class SquatProjectedPoint(
    val x: Float,
    val y: Float,
)

data class SquatCenterCropProjection(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
) {
    fun project(xPx: Double, yPx: Double): SquatProjectedPoint =
        SquatProjectedPoint(
            x = offsetX + xPx.toFloat() * scale,
            y = offsetY + yPx.toFloat() * scale,
        )
}

/**
 * Maps source-frame pixels onto the on-screen frame. [fill] must match the
 * ScaleType of the view showing the frame, or the skeleton will not line up:
 *  - true  -> center-crop (scale = max), matching PreviewView.FILL_CENTER.
 *  - false -> fit-center / letterbox (scale = min), matching ImageView.FIT_CENTER.
 */
fun squatCenterCropProjection(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    targetWidthPx: Float,
    targetHeightPx: Float,
    fill: Boolean = true,
): SquatCenterCropProjection {
    require(sourceWidthPx > 0 && sourceHeightPx > 0) { "Pose source dimensions must be positive" }
    require(targetWidthPx > 0f && targetHeightPx > 0f) { "Pose target dimensions must be positive" }
    val scaleW = targetWidthPx / sourceWidthPx.toFloat()
    val scaleH = targetHeightPx / sourceHeightPx.toFloat()
    val scale = if (fill) max(scaleW, scaleH) else min(scaleW, scaleH)
    return SquatCenterCropProjection(
        scale = scale,
        offsetX = (targetWidthPx - sourceWidthPx * scale) / 2f,
        offsetY = (targetHeightPx - sourceHeightPx * scale) / 2f,
    )
}

fun SquatActivityFrameResult.toAnnotationFrame(
    sourceWidthPx: Int,
    sourceHeightPx: Int,
): SquatAnnotationFrame {
    require(sourceWidthPx > 0 && sourceHeightPx > 0) { "Pose source dimensions must be positive" }
    return SquatAnnotationFrame(
        landmarks = landmarks,
        sourceWidthPx = sourceWidthPx,
        sourceHeightPx = sourceHeightPx,
        view = view,
        tracking = tracking,
        phase = phase,
        repCount = repCount,
        latestScore = latestScore,
        currentCorrection = currentCorrection,
        immediateAlerts = immediateAlerts,
        activeIssues = activeIssues,
        angleAnnotations = squatAngleAnnotations(landmarks, view),
    )
}

fun SquatAnnotationFrame.primaryAlert(): Alert? =
    (immediateAlerts + activeIssues).maxWithOrNull(
        compareBy<Alert> { it.level.wireValue },
    )
