/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.pose

import android.graphics.Bitmap

data class PoseLandmark(
    val xPx: Double,
    val yPx: Double,
    val visibility: Double,
)

typealias PoseLandmarks = Map<String, PoseLandmark>

interface CocoPoseEstimator : AutoCloseable {
    fun run(bitmap: Bitmap): PoseLandmarks
}
