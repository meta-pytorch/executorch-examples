/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RtmwPoseFixtureTest {
    @Test
    fun rtmwFixtureProducesPlausibleLowerBodyLandmarks() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.context.assets.open("rtmw_pose_fixture.jpg").use {
            checkNotNull(BitmapFactory.decodeStream(it))
        }
        val estimator = SquatBackend.productionPoseEstimatorFactory(
            instrumentation.targetContext,
        )()

        val landmarks = try {
            estimator.run(bitmap)
        } finally {
            estimator.close()
        }

        val hips = listOf("l_hip", "r_hip").map(landmarks::getValue)
        val knees = listOf("l_knee", "r_knee").map(landmarks::getValue)
        val ankles = listOf("l_ankle", "r_ankle").map(landmarks::getValue)
        (hips + knees + ankles).forEach { landmark ->
            assertTrue(landmark.xPx.isFinite())
            assertTrue(landmark.yPx.isFinite())
            assertTrue(landmark.visibility.isFinite())
            assertTrue(landmark.xPx in 0.0..bitmap.width.toDouble())
            assertTrue(landmark.yPx in 0.0..bitmap.height.toDouble())
            assertTrue(landmark.visibility > 0.0)
        }

        val hipY = hips.map { it.yPx }.average()
        val kneeY = knees.map { it.yPx }.average()
        val ankleY = ankles.map { it.yPx }.average()
        assertTrue("hips must be above knees", hipY < kneeY)
        assertTrue("knees must be above ankles", kneeY < ankleY)

        val lowerBodyMinX = min(hips.minOf { it.xPx }, ankles.minOf { it.xPx })
        val lowerBodyMaxX = max(hips.maxOf { it.xPx }, ankles.maxOf { it.xPx })
        assertTrue("lower-body span must be visible", lowerBodyMaxX - lowerBodyMinX > 1.0)
    }
}
