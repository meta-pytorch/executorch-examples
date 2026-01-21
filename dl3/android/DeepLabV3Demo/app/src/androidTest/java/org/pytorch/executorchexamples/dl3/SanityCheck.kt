/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.dl3

import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor

@SmallTest
class SanityCheck {

    @Test
    fun testModuleForward() {
        val module = Module.load("/data/local/tmp/dl3_xnnpack_fp32.pte")
        // Test with sample inputs (ones) and make sure there is no crash.
        val outputTensor: Tensor = module.forward()[0].toTensor()
        val shape = outputTensor.shape()
        // batch_size * classes * width * height
        assertEquals(4, shape.size.toLong())
        assertEquals(1, shape[0])
        assertEquals(21, shape[1])
        assertEquals(224, shape[2])
        assertEquals(224, shape[3])
    }
}
