/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.mv3

import android.graphics.Bitmap
import org.pytorch.executorch.Tensor
import java.nio.FloatBuffer

/**
 * Contains utility functions for [Tensor] creation from [android.graphics.Bitmap].
 */
object TensorImageUtils {

    val TORCHVISION_NORM_MEAN_RGB = floatArrayOf(0.485f, 0.456f, 0.406f)
    val TORCHVISION_NORM_STD_RGB = floatArrayOf(0.229f, 0.224f, 0.225f)

    /**
     * Creates new [Tensor] from full [android.graphics.Bitmap], normalized with specified
     * in parameters mean and std.
     *
     * @param normMeanRGB means for RGB channels normalization, length must equal 3, RGB order
     * @param normStdRGB standard deviation for RGB channels normalization, length must equal 3, RGB
     *     order
     */
    fun bitmapToFloat32Tensor(
        bitmap: Bitmap,
        normMeanRGB: FloatArray,
        normStdRGB: FloatArray
    ): Tensor {
        checkNormMeanArg(normMeanRGB)
        checkNormStdArg(normStdRGB)

        return bitmapToFloat32Tensor(
            bitmap, 0, 0, bitmap.width, bitmap.height, normMeanRGB, normStdRGB
        )
    }

    /**
     * Creates new [Tensor] from specified area of [android.graphics.Bitmap], normalized
     * with specified in parameters mean and std.
     */
    private fun bitmapToFloat32Tensor(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        normMeanRGB: FloatArray,
        normStdRGB: FloatArray
    ): Tensor {
        val floatBuffer = Tensor.allocateFloatBuffer(3 * width * height)
        bitmapToFloatBuffer(bitmap, x, y, width, height, normMeanRGB, normStdRGB, floatBuffer, 0)
        return Tensor.fromBlob(floatBuffer, longArrayOf(1, 3, height.toLong(), width.toLong()))
    }

    private fun bitmapToFloatBuffer(
        bitmap: Bitmap,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        normMeanRGB: FloatArray,
        normStdRGB: FloatArray,
        outBuffer: FloatBuffer,
        outBufferOffset: Int
    ) {
        val pixelsCount = height * width
        val pixels = IntArray(pixelsCount)
        bitmap.getPixels(pixels, 0, width, x, y, width, height)
        val offsetG = pixelsCount
        val offsetB = 2 * pixelsCount
        for (i in 0 until pixelsCount) {
            val c = pixels[i]
            val r = ((c shr 16) and 0xff) / 255.0f
            val g = ((c shr 8) and 0xff) / 255.0f
            val b = (c and 0xff) / 255.0f
            outBuffer.put(outBufferOffset + i, (r - normMeanRGB[0]) / normStdRGB[0])
            outBuffer.put(outBufferOffset + offsetG + i, (g - normMeanRGB[1]) / normStdRGB[1])
            outBuffer.put(outBufferOffset + offsetB + i, (b - normMeanRGB[2]) / normStdRGB[2])
        }
    }

    private fun checkNormStdArg(normStdRGB: FloatArray) {
        require(normStdRGB.size == 3) { "normStdRGB length must be 3" }
    }

    private fun checkNormMeanArg(normMeanRGB: FloatArray) {
        require(normMeanRGB.size == 3) { "normMeanRGB length must be 3" }
    }
}
