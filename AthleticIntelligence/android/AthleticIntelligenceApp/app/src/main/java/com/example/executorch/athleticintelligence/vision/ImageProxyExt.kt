/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

internal data class YuvPlane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int,
)

/** Packs any valid YUV_420_888 plane layout into the tightly packed NV21 format. */
internal fun yuv420ToNv21(
    width: Int,
    height: Int,
    y: YuvPlane,
    u: YuvPlane,
    v: YuvPlane,
): ByteArray {
    require(width > 0 && height > 0) { "image dimensions must be positive" }
    val chromaWidth = (width + 1) / 2
    val chromaHeight = (height + 1) / 2
    val output = ByteArray(width * height + chromaWidth * chromaHeight * 2)

    copyPlane(y, width, height, output, outputOffset = 0, outputPixelStride = 1)
    val chromaOffset = width * height
    copyPlane(v, chromaWidth, chromaHeight, output, chromaOffset, outputPixelStride = 2)
    copyPlane(u, chromaWidth, chromaHeight, output, chromaOffset + 1, outputPixelStride = 2)
    return output
}

private fun copyPlane(
    plane: YuvPlane,
    width: Int,
    height: Int,
    output: ByteArray,
    outputOffset: Int,
    outputPixelStride: Int,
) {
    require(plane.rowStride > 0 && plane.pixelStride > 0) { "plane strides must be positive" }
    val source = plane.buffer.duplicate()
    val sourceOrigin = source.position()
    for (row in 0 until height) {
        for (column in 0 until width) {
            val sourceIndex = sourceOrigin + row * plane.rowStride + column * plane.pixelStride
            require(sourceIndex < source.limit()) { "plane buffer is smaller than its stride metadata" }
            val outputIndex = outputOffset + (row * width + column) * outputPixelStride
            output[outputIndex] = source.get(sourceIndex)
        }
    }
}

/** Converts a CameraX YUV_420_888 ImageProxy to an upright RGB Bitmap. */
fun ImageProxy.toUprightBitmap(): Bitmap {
    require(format == ImageFormat.YUV_420_888) { "expected YUV_420_888, got format $format" }
    require(planes.size == 3) { "expected 3 YUV planes, got ${planes.size}" }
    val imagePlanes = planes.map { plane ->
        YuvPlane(plane.buffer, plane.rowStride, plane.pixelStride)
    }
    val nv21 = yuv420ToNv21(width, height, imagePlanes[0], imagePlanes[1], imagePlanes[2])

    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), 85, out)
    val bytes = out.toByteArray()
    val bmp = checkNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) {
        "failed to decode the camera frame"
    }

    val rot = imageInfo.rotationDegrees
    if (rot == 0) return bmp
    val m = Matrix().apply { postRotate(rot.toFloat()) }
    return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
}
