/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.dl3

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File

/**
 * Sanity check test for model loading.
 * 
 * This test will skip if the model file is not available.
 * The model should be in the app's private storage (same location as MainActivity uses).
 */
@SmallTest
class SanityCheck {

    companion object {
        private const val MODEL_FILENAME = "dl3_xnnpack_fp32.pte"
    }

    private lateinit var modelPath: String

    @Before
    fun setUp() {
        // Use the app's private files directory (same as MainActivity)
        val context = ApplicationProvider.getApplicationContext<Context>()
        modelPath = "${context.filesDir.absolutePath}/$MODEL_FILENAME"
        
        // Check if model file exists, skip test if not
        val modelFile = File(modelPath)
        assumeTrue(
            "Model file not found at $modelPath. Run UIWorkflowTest first to download the model.",
            modelFile.exists()
        )
    }

    @Test
    fun testModuleForward() {
        val module = Module.load(modelPath)
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
