/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.mv3

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.exp

/**
 * Instrumentation test for MobileNetV3 image classification demo.
 *
 * This test validates the complete end-to-end workflow:
 * 1. App launches successfully
 * 2. Model downloads if needed
 * 3. Downloads a cat image from HuggingFace
 * 4. Runs inference and validates the image is classified as a cat
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class UIWorkflowTest {

    companion object {
        private const val TAG = "MV3UIWorkflowTest"
        private const val RESULT_TAG = "MV3_RESULT"

        // Cat test image from HuggingFace
        private const val CAT_IMAGE_URL =
            "https://huggingface.co/datasets/huggingface/documentation-images/resolve/main/transformers/tasks/cat.jpg"

        // Model filename (same as MainActivity)
        private const val MODEL_FILENAME = "mv3.pte"

        // Cat-related ImageNet classes that we expect for a cat image
        private val CAT_CLASSES = setOf(
            "tabby", "tiger cat", "Persian cat", "Siamese cat", "Egyptian cat"
        )
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    /**
     * Downloads an image from URL and returns it as a Bitmap.
     */
    private fun downloadImageFromUrl(imageUrl: String): Bitmap? {
        var bitmap: Bitmap? = null
        val latch = CountDownLatch(1)

        Thread {
            try {
                Log.i(TAG, "Downloading image from: $imageUrl")
                val url = URL(imageUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { inputStream ->
                        bitmap = BitmapFactory.decodeStream(inputStream)
                    }
                    Log.i(TAG, "Image downloaded successfully")
                } else {
                    Log.e(TAG, "Failed to download image: HTTP ${connection.responseCode}")
                }
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading image", e)
            } finally {
                latch.countDown()
            }
        }.start()

        latch.await(60, TimeUnit.SECONDS)
        return bitmap
    }

    /**
     * Waits for the model to be ready.
     */
    private fun waitForModelReady(timeoutMs: Long = 120000): Boolean {
        return try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
                composeTestRule
                    .onAllNodesWithText("Pick an image to start or use Live Camera", substring = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            Log.i(TAG, "Model is ready")
            true
        } catch (e: Exception) {
            Log.i(TAG, "Model not ready after ${timeoutMs}ms: ${e.message}")
            false
        }
    }

    /**
     * Ensures model is ready, downloading if necessary.
     */
    private fun ensureModelReady(): Boolean {
        composeTestRule.waitForIdle()

        // Check if model is already ready
        val readyNodes = composeTestRule
            .onAllNodesWithText("Pick an image to start or use Live Camera", substring = true)
            .fetchSemanticsNodes()
        if (readyNodes.isNotEmpty()) {
            Log.i(TAG, "Model is already ready")
            return true
        }

        // Check if we need to download
        val downloadNodes = composeTestRule
            .onAllNodesWithText("Download Model", substring = true)
            .fetchSemanticsNodes()

        if (downloadNodes.isNotEmpty()) {
            Log.i(TAG, "Downloading model...")
            composeTestRule.onNodeWithText("Download Model").performClick()

            // Wait for download to complete (up to 5 minutes)
            composeTestRule.waitUntil(timeoutMillis = 300000) {
                val downloading = composeTestRule
                    .onAllNodesWithText("Downloading...", substring = true)
                    .fetchSemanticsNodes()
                val ready = composeTestRule
                    .onAllNodesWithText("Pick an image to start or use Live Camera", substring = true)
                    .fetchSemanticsNodes()
                downloading.isEmpty() && ready.isNotEmpty()
            }
            Log.i(TAG, "Model download complete")
            return true
        }

        // Wait for UI to settle
        return waitForModelReady(10000)
    }

    /**
     * Applies softmax to convert logits to probabilities.
     */
    private fun softmax(scores: FloatArray): FloatArray {
        val max = scores.maxOrNull() ?: 0f
        val expScores = scores.map { exp((it - max).toDouble()) }
        val sumExp = expScores.sum()
        return expScores.map { (it / sumExp).toFloat() }.toFloatArray()
    }

    /**
     * Gets top-K predictions from scores.
     */
    private fun getTopK(scores: FloatArray, k: Int): List<Pair<Int, Float>> {
        val probabilities = softmax(scores)
        return probabilities.withIndex()
            .sortedByDescending { it.value }
            .take(k)
            .map { it.index to it.value }
    }

    /**
     * Runs inference on the given bitmap using the model.
     */
    private fun runInferenceOnBitmap(bitmap: Bitmap, module: Module): List<Pair<String, Float>> {
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

        val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
            scaledBitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        val outputTensor = module.forward(EValue.from(inputTensor))[0].toTensor()
        val scores = outputTensor.dataAsFloatArray
        val top3 = getTopK(scores, 3)

        return top3.map { (index, score) ->
            val label = if (index in ImageNetClasses.IMAGENET_CLASSES.indices) {
                ImageNetClasses.IMAGENET_CLASSES[index]
            } else {
                "Unknown($index)"
            }
            label to score
        }
    }

    /**
     * Tests the full end-to-end classification workflow:
     * 1. App launches
     * 2. Download model if needed
     * 3. Download cat image from HuggingFace
     * 4. Run inference
     * 5. Validate that the result is a cat class
     */
    @Test
    fun testCatImageClassification() {
        composeTestRule.waitForIdle()

        // Step 1: Ensure model is ready
        val modelReady = ensureModelReady()
        assertTrue("Model should be ready or download should start", modelReady)

        val finalReady = waitForModelReady(300000)
        assertTrue("Model should be ready", finalReady)

        // Step 2: Download cat image
        val bitmap = downloadImageFromUrl(CAT_IMAGE_URL)
        assertNotNull("Cat image should be downloaded", bitmap)

        // Step 3: Load the model
        val modelPath = context.filesDir.absolutePath + "/" + MODEL_FILENAME
        val modelFile = File(modelPath)
        assertTrue("Model file should exist at $modelPath", modelFile.exists())

        val module = Module.load(modelPath)
        assertNotNull("Module should be loaded", module)

        // Step 4: Run inference
        val results = runInferenceOnBitmap(bitmap!!, module)
        assertTrue("Should have classification results", results.isNotEmpty())

        Log.i(RESULT_TAG, "Classification results:")
        results.forEach { (label, prob) ->
            Log.i(RESULT_TAG, "  $label: ${String.format("%.4f", prob)}")
        }

        // Step 5: Validate that top prediction is a cat
        val topLabel = results.first().first
        val isCat = CAT_CLASSES.any { catClass ->
            topLabel.contains(catClass, ignoreCase = true)
        }

        assertTrue(
            "Top prediction should be a cat class, but got: $topLabel. Expected one of: $CAT_CLASSES",
            isCat
        )

        Log.i(TAG, "Cat image correctly classified as: $topLabel")
    }
}
