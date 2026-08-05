/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.model

import android.content.Context
import android.graphics.Bitmap
import org.pytorch.executorch.DType
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File

/**
 * Seam over a pose model's `run`/`close` surface. JVM tests can substitute a
 * scripted implementation without loading a native `.pte` module.
 */
interface PoseModel {
    fun run(src: Bitmap, box: RectI): PoseResult
    fun close()
}

/**
 * RTMW body-133 SimCC pose model on the ExecuTorch XNNPACK
 * runtime. Handles letterbox crop -> ImageNet-normalized NCHW input -> forward ->
 * SimCC argmax decode back to input-image pixel coordinates.
 */
class SimccPose(
    context: Context,
    assetName: String,
    val numKpts: Int,
    private val inW: Int,
    private val inH: Int,
) : PoseModel {
    private val module: Module

    init {
        val f = copyAsset(context, assetName)
        module = Module.load(f.absolutePath)
    }

    /**
     * Run pose on the given crop box of [src]. Returns keypoints in [src] pixel
     * coordinates plus per-keypoint confidence.
     */
    override fun run(src: Bitmap, box: RectI): PoseResult {
        // letterbox the box region into inW x inH, preserving aspect ratio
        val transform = simccLetterboxTransform(
            box = box,
            inputWidth = inW,
            inputHeight = inH,
            splitRatio = SPLIT_RATIO.toInt(),
        )

        val input = FloatArray(3 * inH * inW)
        // fill with normalized 0 (black) then paint the scaled region
        val meanR = 123.675f; val meanG = 116.28f; val meanB = 103.53f
        val stdR = 58.395f; val stdG = 57.12f; val stdB = 57.375f
        // pre-fill padding as normalized black
        val chw = inH * inW
        for (i in 0 until chw) {
            input[i] = -meanR / stdR
            input[chw + i] = -meanG / stdG
            input[2 * chw + i] = -meanB / stdB
        }
        val srcW = src.width; val srcH = src.height
        for (y in 0 until inH) {
            val fy = (y - transform.padY) / transform.scale + box.top
            if (fy < 0 || fy >= srcH) continue
            val syi = fy.toInt()
            for (x in 0 until inW) {
                val fx = (x - transform.padX) / transform.scale + box.left
                if (fx < 0 || fx >= srcW) continue
                val p = src.getPixel(fx.toInt(), syi)
                val r = (p shr 16 and 0xff).toFloat()
                val g = (p shr 8 and 0xff).toFloat()
                val b = (p and 0xff).toFloat()
                val idx = y * inW + x
                input[idx] = (r - meanR) / stdR
                input[chw + idx] = (g - meanG) / stdG
                input[2 * chw + idx] = (b - meanB) / stdB
            }
        }

        val t = Tensor.fromBlob(input, longArrayOf(1, 3, inH.toLong(), inW.toLong()))
        val out = module.forward(EValue.from(t))
        val tensors = arrayOfNulls<Tensor>(out.size)
        val metadata = out.mapIndexed { index, output ->
            if (!output.isTensor()) {
                SimccOutputMetadata(isTensor = false)
            } else {
                val tensor = output.toTensor().also { tensors[index] = it }
                SimccOutputMetadata(
                    isTensor = true,
                    elementType = if (tensor.dtype() == DType.FLOAT) {
                        SimccElementType.FLOAT32
                    } else {
                        SimccElementType.OTHER
                    },
                    shape = tensor.shape(),
                )
            }
        }
        validateSimccOutputMetadata(
            outputs = metadata,
            keypointCount = numKpts,
            inputWidth = inW,
            inputHeight = inH,
            splitRatio = SPLIT_RATIO.toInt(),
        )

        return decodeSimccOutputs(
            scoresX = checkNotNull(tensors[0]).dataAsFloatArray,
            scoresY = checkNotNull(tensors[1]).dataAsFloatArray,
            keypointCount = numKpts,
            transform = transform,
        )
    }

    override fun close() = module.destroy()

    private fun copyAsset(ctx: Context, name: String): File {
        val destination = File(File(ctx.filesDir, "executorch-models"), name).toPath()
        return installModelAsset(destination) { ctx.assets.open(name) }.toFile()
    }

    companion object {
        const val SPLIT_RATIO = 2.0f
    }
}

data class PoseResult(val kpts: FloatArray, val conf: FloatArray)

data class RectI(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun width() = (right - left).coerceAtLeast(1)
    fun height() = (bottom - top).coerceAtLeast(1)
}
