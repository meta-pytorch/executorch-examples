/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SimccDecoderTest {

    @Test
    fun `accepts exact float x and y tensor shapes`() {
        validateSimccOutputMetadata(validMetadata(), KEYPOINT_COUNT, INPUT_WIDTH, INPUT_HEIGHT)
    }

    @Test
    fun `rejects any output count other than x and y tensors`() {
        val failure = assertThrows(IllegalStateException::class.java) {
            validateSimccOutputMetadata(emptyList(), KEYPOINT_COUNT, INPUT_WIDTH, INPUT_HEIGHT)
        }

        assertTrue(failure.message.orEmpty().contains("exactly 2"))
    }

    @Test
    fun `rejects non-tensor and non-float outputs before decoding`() {
        val nonTensor = validMetadata().toMutableList().also {
            it[0] = SimccOutputMetadata(isTensor = false)
        }
        val nonTensorFailure = assertThrows(IllegalStateException::class.java) {
            validateSimccOutputMetadata(nonTensor, KEYPOINT_COUNT, INPUT_WIDTH, INPUT_HEIGHT)
        }
        assertTrue(nonTensorFailure.message.orEmpty().contains("output 0"))
        assertTrue(nonTensorFailure.message.orEmpty().contains("tensor"))

        val nonFloat = validMetadata().toMutableList().also {
            it[1] = it[1].copy(elementType = SimccElementType.OTHER)
        }
        val nonFloatFailure = assertThrows(IllegalStateException::class.java) {
            validateSimccOutputMetadata(nonFloat, KEYPOINT_COUNT, INPUT_WIDTH, INPUT_HEIGHT)
        }
        assertTrue(nonFloatFailure.message.orEmpty().contains("output 1"))
        assertTrue(nonFloatFailure.message.orEmpty().contains("float32"))
    }

    @Test
    fun `rejects wrong keypoint and bin dimensions for either axis`() {
        val wrongX = validMetadata().toMutableList().also {
            it[0] = it[0].copy(shape = longArrayOf(1, KEYPOINT_COUNT.toLong(), X_BINS - 1L))
        }
        val wrongXFailure = assertThrows(IllegalStateException::class.java) {
            validateSimccOutputMetadata(wrongX, KEYPOINT_COUNT, INPUT_WIDTH, INPUT_HEIGHT)
        }
        assertTrue(wrongXFailure.message.orEmpty().contains("output 0"))
        assertTrue(wrongXFailure.message.orEmpty().contains(X_BINS.toString()))

        val wrongY = validMetadata().toMutableList().also {
            it[1] = it[1].copy(
                shape = longArrayOf(1, (KEYPOINT_COUNT - 1).toLong(), Y_BINS.toLong()),
            )
        }
        val wrongYFailure = assertThrows(IllegalStateException::class.java) {
            validateSimccOutputMetadata(wrongY, KEYPOINT_COUNT, INPUT_WIDTH, INPUT_HEIGHT)
        }
        assertTrue(wrongYFailure.message.orEmpty().contains("output 1"))
        assertTrue(wrongYFailure.message.orEmpty().contains(KEYPOINT_COUNT.toString()))
    }

    @Test
    fun `decodes portrait full-frame coordinates through horizontal padding and height scale`() {
        val transform = simccLetterboxTransform(
            box = RectI(0, 0, 1080, 1920),
            inputWidth = INPUT_WIDTH,
            inputHeight = INPUT_HEIGHT,
        )
        val scoresX = scores(KEYPOINT_COUNT, X_BINS)
        val scoresY = scores(KEYPOINT_COUNT, Y_BINS)
        setPeak(scoresX, X_BINS, keypoint = 11, bin = 360, score = 0.88f)
        setPeak(scoresY, Y_BINS, keypoint = 11, bin = 512, score = 0.93f)

        val result = decodeSimccOutputs(scoresX, scoresY, KEYPOINT_COUNT, transform)

        assertEquals(0.2f, transform.scale, 1e-6f)
        assertEquals(36f, transform.padX, 1e-6f)
        assertEquals(0f, transform.padY, 1e-6f)
        assertEquals(720f, result.kpts[11 * 2], 1e-4f)
        assertEquals(1280f, result.kpts[11 * 2 + 1], 1e-4f)
        assertEquals(0.88f, result.conf[11], 1e-6f)
    }

    @Test
    fun `decodes landscape coordinates through vertical padding and width scale`() {
        val transform = simccLetterboxTransform(
            box = RectI(0, 0, 1920, 1080),
            inputWidth = INPUT_WIDTH,
            inputHeight = INPUT_HEIGHT,
        )
        val scoresX = scores(KEYPOINT_COUNT, X_BINS)
        val scoresY = scores(KEYPOINT_COUNT, Y_BINS)
        setPeak(scoresX, X_BINS, keypoint = 12, bin = 384, score = 0.91f)
        setPeak(scoresY, Y_BINS, keypoint = 12, bin = 438, score = 0.84f)

        val result = decodeSimccOutputs(scoresX, scoresY, KEYPOINT_COUNT, transform)

        assertEquals(0.15f, transform.scale, 1e-6f)
        assertEquals(0f, transform.padX, 1e-6f)
        assertEquals(111f, transform.padY, 1e-6f)
        assertEquals(1280f, result.kpts[12 * 2], 1e-4f)
        assertEquals(720f, result.kpts[12 * 2 + 1], 1e-4f)
        assertEquals(0.84f, result.conf[12], 1e-6f)
    }

    @Test
    fun `rejects score arrays that do not match validated bin dimensions`() {
        val transform = simccLetterboxTransform(
            box = RectI(0, 0, 1080, 1920),
            inputWidth = INPUT_WIDTH,
            inputHeight = INPUT_HEIGHT,
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            decodeSimccOutputs(
                scoresX = FloatArray(KEYPOINT_COUNT * X_BINS - 1),
                scoresY = FloatArray(KEYPOINT_COUNT * Y_BINS),
                keypointCount = KEYPOINT_COUNT,
                transform = transform,
            )
        }

        assertTrue(failure.message.orEmpty().contains("x-axis"))
    }

    companion object {
        private const val KEYPOINT_COUNT = 133
        private const val INPUT_WIDTH = 288
        private const val INPUT_HEIGHT = 384
        private const val X_BINS = INPUT_WIDTH * 2
        private const val Y_BINS = INPUT_HEIGHT * 2

        private fun validMetadata(): List<SimccOutputMetadata> = listOf(
            SimccOutputMetadata(
                isTensor = true,
                elementType = SimccElementType.FLOAT32,
                shape = longArrayOf(1, KEYPOINT_COUNT.toLong(), X_BINS.toLong()),
            ),
            SimccOutputMetadata(
                isTensor = true,
                elementType = SimccElementType.FLOAT32,
                shape = longArrayOf(1, KEYPOINT_COUNT.toLong(), Y_BINS.toLong()),
            ),
        )

        private fun scores(keypoints: Int, bins: Int): FloatArray =
            FloatArray(keypoints * bins) { -100f }

        private fun setPeak(
            scores: FloatArray,
            bins: Int,
            keypoint: Int,
            bin: Int,
            score: Float,
        ) {
            scores[keypoint * bins + bin] = score
        }
    }
}
