/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.vision

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ImageProxyExtTest {
    @Test
    fun `converts padded planar YUV planes to tightly packed NV21`() {
        val y = plane(
            bytes = byteArrayOf(1, 2, 3, 4, 99, 99, 5, 6, 7, 8, 99, 99),
            rowStride = 6,
            pixelStride = 1,
        )
        val u = plane(byteArrayOf(11, 99, 12, 99), rowStride = 4, pixelStride = 2)
        val v = plane(byteArrayOf(21, 99, 22, 99), rowStride = 4, pixelStride = 2)

        val nv21 = yuv420ToNv21(width = 4, height = 2, y = y, u = u, v = v)

        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 21, 11, 22, 12),
            nv21,
        )
    }

    @Test
    fun `honors non-unit luma pixel stride`() {
        val y = plane(
            bytes = byteArrayOf(1, 99, 2, 99, 3, 99, 4, 99),
            rowStride = 8,
            pixelStride = 2,
        )
        val u = plane(byteArrayOf(11, 12), rowStride = 2, pixelStride = 1)
        val v = plane(byteArrayOf(21, 22), rowStride = 2, pixelStride = 1)

        val nv21 = yuv420ToNv21(width = 4, height = 1, y = y, u = u, v = v)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), nv21.copyOfRange(0, 4))
    }

    private fun plane(bytes: ByteArray, rowStride: Int, pixelStride: Int) = YuvPlane(
        buffer = ByteBuffer.wrap(bytes),
        rowStride = rowStride,
        pixelStride = pixelStride,
    )
}
