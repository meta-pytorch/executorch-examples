/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.dl3

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sanity check test for model loading.
 * 
 * This test downloads the model if not available and validates model functionality.
 * The model is stored in the app's private storage (same location as MainActivity uses).
 */
@SmallTest
class SanityCheck {

    companion object {
        private const val MODEL_FILENAME = "dl3_xnnpack_fp32.pte"
        private const val MODEL_URL = "https://ossci-android.s3.amazonaws.com/executorch/models/snapshot-20260116/dl3_xnnpack_fp32.pte"
        private const val TAG = "SanityCheck"
    }

    private lateinit var modelPath: String
    private lateinit var context: Context

    @Before
    fun setUp() {
        // Use the app's private files directory (same as MainActivity)
        context = ApplicationProvider.getApplicationContext()
        modelPath = "${context.filesDir.absolutePath}/$MODEL_FILENAME"
        
        // Download model if not present
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.i(TAG, "Model not found at $modelPath, downloading...")
            downloadModel()
        } else {
            Log.i(TAG, "Model found at $modelPath")
        }
    }

    private fun downloadModel() {
        try {
            val url = URL(MODEL_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw RuntimeException("Server returned HTTP ${connection.responseCode}")
            }

            connection.inputStream.use { input ->
                FileOutputStream(modelPath).use { output ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            Log.i(TAG, "Model downloaded successfully to $modelPath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download model", e)
            throw RuntimeException("Failed to download model: ${e.message}", e)
        }
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
