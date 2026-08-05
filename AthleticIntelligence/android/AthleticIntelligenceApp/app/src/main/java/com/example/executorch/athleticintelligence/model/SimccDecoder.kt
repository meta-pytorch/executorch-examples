/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.model

/** Host-testable description of one ExecuTorch output before tensor data is read. */
internal data class SimccOutputMetadata(
    val isTensor: Boolean,
    val elementType: SimccElementType? = null,
    val shape: LongArray = longArrayOf(),
)

internal enum class SimccElementType {
    FLOAT32,
    OTHER,
}

/**
 * Validates the RTMW/SimCC two-tensor contract before either tensor is decoded.
 * Output 0 is x-axis scores `[1, K, inputWidth * splitRatio]`; output 1 is the
 * corresponding y-axis tensor based on input height.
 */
internal fun validateSimccOutputMetadata(
    outputs: List<SimccOutputMetadata>,
    keypointCount: Int,
    inputWidth: Int,
    inputHeight: Int,
    splitRatio: Int = DEFAULT_SIMCC_SPLIT_RATIO,
) {
    check(outputs.size == 2) { "expected exactly 2 SimCC model outputs, got ${outputs.size}" }

    val expectedShapes = listOf(
        longArrayOf(1, keypointCount.toLong(), (inputWidth * splitRatio).toLong()),
        longArrayOf(1, keypointCount.toLong(), (inputHeight * splitRatio).toLong()),
    )
    outputs.forEachIndexed { index, output ->
        check(output.isTensor) { "expected SimCC output $index to be a tensor" }
        check(output.elementType == SimccElementType.FLOAT32) {
            "expected SimCC output $index to be float32"
        }
        val expectedShape = expectedShapes[index]
        check(output.shape.contentEquals(expectedShape)) {
            "expected SimCC output $index shape ${expectedShape.contentToString()}, " +
                "got ${output.shape.contentToString()}"
        }
    }
}

/** Geometry needed to invert one letterbox operation back into source pixels. */
internal data class SimccLetterboxTransform(
    val box: RectI,
    val inputWidth: Int,
    val inputHeight: Int,
    val splitRatio: Int,
    val scale: Float,
    val padX: Float,
    val padY: Float,
) {
    val xBins: Int get() = inputWidth * splitRatio
    val yBins: Int get() = inputHeight * splitRatio
}

internal fun simccLetterboxTransform(
    box: RectI,
    inputWidth: Int,
    inputHeight: Int,
    splitRatio: Int = DEFAULT_SIMCC_SPLIT_RATIO,
): SimccLetterboxTransform {
    require(inputWidth > 0) { "input width must be positive" }
    require(inputHeight > 0) { "input height must be positive" }
    require(splitRatio > 0) { "SimCC split ratio must be positive" }

    val scale = minOf(
        inputWidth.toFloat() / box.width(),
        inputHeight.toFloat() / box.height(),
    )
    val scaledWidth = box.width() * scale
    val scaledHeight = box.height() * scale
    return SimccLetterboxTransform(
        box = box,
        inputWidth = inputWidth,
        inputHeight = inputHeight,
        splitRatio = splitRatio,
        scale = scale,
        padX = (inputWidth - scaledWidth) / 2f,
        padY = (inputHeight - scaledHeight) / 2f,
    )
}

/** Argmax-decodes validated SimCC tensors and reverses letterboxing to source pixels. */
internal fun decodeSimccOutputs(
    scoresX: FloatArray,
    scoresY: FloatArray,
    keypointCount: Int,
    transform: SimccLetterboxTransform,
): PoseResult {
    val expectedXValues = keypointCount * transform.xBins
    val expectedYValues = keypointCount * transform.yBins
    require(scoresX.size == expectedXValues) {
        "expected $expectedXValues x-axis SimCC scores, got ${scoresX.size}"
    }
    require(scoresY.size == expectedYValues) {
        "expected $expectedYValues y-axis SimCC scores, got ${scoresY.size}"
    }

    val keypoints = FloatArray(keypointCount * 2)
    val confidence = FloatArray(keypointCount)
    for (keypoint in 0 until keypointCount) {
        val (bestX, bestXScore) = argmax(scoresX, keypoint * transform.xBins, transform.xBins)
        val (bestY, bestYScore) = argmax(scoresY, keypoint * transform.yBins, transform.yBins)
        val inputX = bestX.toFloat() / transform.splitRatio
        val inputY = bestY.toFloat() / transform.splitRatio
        keypoints[keypoint * 2] =
            (inputX - transform.padX) / transform.scale + transform.box.left
        keypoints[keypoint * 2 + 1] =
            (inputY - transform.padY) / transform.scale + transform.box.top
        confidence[keypoint] = minOf(bestXScore, bestYScore)
    }
    return PoseResult(keypoints, confidence)
}

private fun argmax(values: FloatArray, offset: Int, count: Int): Pair<Int, Float> {
    var bestIndex = 0
    var bestValue = -1e9f
    for (index in 0 until count) {
        val value = values[offset + index]
        if (value > bestValue) {
            bestValue = value
            bestIndex = index
        }
    }
    return bestIndex to bestValue
}

private const val DEFAULT_SIMCC_SPLIT_RATIO = 2
