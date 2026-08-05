/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.pose

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoseTypesTest {
    @Test
    fun `squat aliases and shared pose types are interchangeable`() {
        val shared = PoseLandmark(12.0, 34.0, 0.8)
        val squatLandmark: com.example.executorch.athleticintelligence.squat.Landmark = shared
        val sharedMap: PoseLandmarks = mapOf("nose" to shared)
        val squatMap: com.example.executorch.athleticintelligence.squat.Landmarks = sharedMap

        assertEquals(12.0, squatLandmark.xPx, 0.0)
        assertEquals(sharedMap, squatMap)
    }

    @Test
    fun `coco pose estimator owns a closeable bitmap to landmarks contract`() {
        val estimator = object : CocoPoseEstimator {
            override fun run(bitmap: Bitmap): PoseLandmarks = emptyMap()
            override fun close() = Unit
        }

        assertTrue(estimator is AutoCloseable)
    }

}
