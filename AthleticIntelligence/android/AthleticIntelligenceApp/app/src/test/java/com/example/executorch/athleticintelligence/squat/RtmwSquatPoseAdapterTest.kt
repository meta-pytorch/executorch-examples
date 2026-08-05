/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.squat

import android.content.Context
import android.graphics.Bitmap
import com.example.executorch.athleticintelligence.model.PoseModel
import com.example.executorch.athleticintelligence.model.PoseResult
import com.example.executorch.athleticintelligence.model.RectI
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class RtmwSquatPoseAdapterTest {

    private class RecordingPoseModel(
        private val result: PoseResult = rtmwResult(),
    ) : PoseModel {
        var source: Bitmap? = null
        var box: RectI? = null
        var closeCount = 0

        override fun run(src: Bitmap, box: RectI): PoseResult {
            source = src
            this.box = box
            return result
        }

        override fun close() {
            closeCount++
        }
    }

    @Test
    fun `validates the complete RTMW body-133 output contract`() {
        assertThrows(IllegalArgumentException::class.java) {
            rtmwToSquatLandmarks(
                PoseResult(
                    kpts = FloatArray((RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT - 1) * 2),
                    conf = FloatArray(RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT),
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            rtmwToSquatLandmarks(
                PoseResult(
                    kpts = FloatArray(RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT * 2),
                    conf = FloatArray(RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT - 1),
                ),
            )
        }
    }

    @Test
    fun `maps only the first 17 RTMW keypoints in COCO order`() {
        val expectedMapping = listOf(
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
        val result = rtmwResult().also { pose ->
            expectedMapping.forEach { (index, _) ->
                setPoint(
                    pose,
                    index,
                    x = 100f + index,
                    y = 200f + index * 2f,
                    confidence = 0.5f + index / 100f,
                )
            }
            setPoint(pose, 17, 900f, 901f, 0.01f)
        }

        val landmarks = rtmwToSquatLandmarks(result)

        assertEquals(13, landmarks.size)
        assertEquals(expectedMapping.map { it.second }.toSet(), landmarks.keys)
        expectedMapping.forEach { (index, name) ->
            assertLandmark(
                landmarks,
                name,
                x = 100.0 + index,
                y = 200.0 + index * 2.0,
                confidence = 0.5 + index / 100.0,
            )
        }
    }

    @Test
    fun `preserves every selected keypoint confidence`() {
        val result = rtmwResult().also {
            setPoint(it, 13, 123f, 456f, 0.42f)
            setPoint(it, 14, 321f, 654f, 0.17f)
        }

        val landmarks = rtmwToSquatLandmarks(result)

        assertLandmark(landmarks, "l_knee", 123.0, 456.0, 0.42)
        assertLandmark(landmarks, "r_knee", 321.0, 654.0, 0.17)
    }

    @Test
    fun `runs the full upright bitmap and returns full-frame pixel coordinates`() {
        val expected = rtmwResult().also {
            setPoint(it, 11, 720f, 1280f, 0.88f)
        }
        val model = RecordingPoseModel(expected)
        val adapter = RtmwSquatPoseAdapter(model)
        val bitmap = Mockito.mock(Bitmap::class.java)
        Mockito.`when`(bitmap.width).thenReturn(1080)
        Mockito.`when`(bitmap.height).thenReturn(1920)

        val landmarks = adapter.run(bitmap)

        assertSame(bitmap, model.source)
        assertEquals(RectI(0, 0, 1080, 1920), model.box)
        assertLandmark(landmarks, "l_hip", 720.0, 1280.0, 0.88)
    }

    @Test
    fun `close is idempotent and closes the SimCC model once`() {
        val model = RecordingPoseModel()
        val adapter = RtmwSquatPoseAdapter(model)

        adapter.close()
        adapter.close()

        assertEquals(1, model.closeCount)
    }

    @Test
    fun `production factory configures RTMW-l at 384 by 288 and returns the adapter`() {
        val context = Mockito.mock(Context::class.java)
        val model = RecordingPoseModel()
        var invocationCount = 0
        val factory = SquatBackend.productionPoseEstimatorFactory(
            context = context,
            modelFactory = { actualContext, assetName, numKeypoints, inputWidth, inputHeight ->
                invocationCount++
                assertSame(context, actualContext)
                assertEquals("rtmw_l_int8.pte", assetName)
                assertEquals(133, numKeypoints)
                assertEquals(288, inputWidth)
                assertEquals(384, inputHeight)
                model
            },
        )

        val estimator = factory()

        assertTrue(estimator is RtmwSquatPoseAdapter)
        assertEquals(1, invocationCount)
    }

    @Test
    fun `production factory reports a missing RTMW model asset`() {
        val missing = FileNotFoundException("asset is absent")
        val factory = SquatBackend.productionPoseEstimatorFactory(
            context = Mockito.mock(Context::class.java),
            modelFactory = { _, _, _, _, _ -> throw missing },
        )

        val failure = assertThrows(IllegalStateException::class.java) { factory() }

        assertTrue(failure.message.orEmpty().contains("rtmw_l_int8.pte"))
        assertSame(missing, failure.cause)
    }

    companion object {
        private fun rtmwResult(): PoseResult = PoseResult(
            kpts = FloatArray(RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT * 2),
            conf = FloatArray(RtmwSquatPoseAdapter.RTMW_KEYPOINT_COUNT),
        )

        private fun setPoint(result: PoseResult, index: Int, x: Float, y: Float, confidence: Float) {
            result.kpts[index * 2] = x
            result.kpts[index * 2 + 1] = y
            result.conf[index] = confidence
        }

        private fun assertLandmark(
            landmarks: Landmarks,
            name: String,
            x: Double,
            y: Double,
            confidence: Double,
        ) {
            val landmark = landmarks.getValue(name)
            assertEquals(x, landmark.xPx, 1e-6)
            assertEquals(y, landmark.yPx, 1e-6)
            assertEquals(confidence, landmark.visibility, 1e-6)
        }
    }
}
