/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import android.graphics.Bitmap
import com.example.executorch.athleticintelligence.model.PoseModel
import com.example.executorch.athleticintelligence.model.PoseResult
import com.example.executorch.athleticintelligence.model.RectI

/**
 * Adapts RTMW-l body-133 [PoseModel] output to the 13 COCO landmarks consumed
 * by the squat pipeline. [PoseModel] (normally `SimccPose`) owns model input
 * preparation and converts its SimCC result back into source-bitmap pixels.
 */
class RtmwSquatPoseAdapter(
    private val model: PoseModel,
) : SquatPoseEstimator {
    private var closed = false

    /** Runs RTMW-l against the complete upright frame, without a person crop. */
    @Synchronized
    override fun run(bitmap: Bitmap): Landmarks {
        check(!closed) { "RtmwSquatPoseAdapter.run() called after close()" }
        val fullFrame = RectI(left = 0, top = 0, right = bitmap.width, bottom = bitmap.height)
        return rtmwToSquatLandmarks(model.run(bitmap, fullFrame))
    }

    /** Closes the native model at most once. */
    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        model.close()
    }

    companion object {
        const val MODEL_ASSET = "rtmw_l_int8.pte"
        const val RTMW_KEYPOINT_COUNT = 133
        const val MODEL_INPUT_WIDTH = 288
        const val MODEL_INPUT_HEIGHT = 384
    }
}

/**
 * Validates body-133 output, then consumes its COCO-compatible first 17
 * keypoints. Coordinates and per-keypoint confidence are preserved verbatim.
 */
internal fun rtmwToSquatLandmarks(result: PoseResult): Landmarks {
    val expectedCoordinateCount = RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT * 2
    require(result.kpts.size == expectedCoordinateCount) {
        "expected $expectedCoordinateCount RTMW coordinate values " +
            "(${RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT} keypoints), got ${result.kpts.size}"
    }
    require(result.conf.size == RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT) {
        "expected ${RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT} RTMW confidence values, " +
            "got ${result.conf.size}"
    }

    return RTMW_COCO_TO_SQUAT.associate { (index, name) ->
        name to Landmark(
            xPx = result.kpts[index * 2].toDouble(),
            yPx = result.kpts[index * 2 + 1].toDouble(),
            visibility = result.conf[index].toDouble(),
        )
    }
}

/** RTMW body-133 begins with these 13 squat-supported points in COCO-17 order. */
private val RTMW_COCO_TO_SQUAT = listOf(
    0 to "nose",
    5 to "l_shoulder",
    6 to "r_shoulder",
    7 to "l_elbow",
    8 to "r_elbow",
    9 to "l_wrist",
    10 to "r_wrist",
    11 to "l_hip",
    12 to "r_hip",
    13 to "l_knee",
    14 to "r_knee",
    15 to "l_ankle",
    16 to "r_ankle",
)
