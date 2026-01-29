/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumentation test for Gemma multimodal image understanding.
 *
 * This test validates:
 * 1. App launches and Gemma model loads successfully
 * 2. Downloads a cat image from HuggingFace
 * 3. Attaches the image via stubbed Gallery picker and asks "What is in this image?"
 * 4. Validates the response contains "cat"
 *
 * Prerequisites:
 * - Push Gemma model files to /data/local/tmp/llama/
 *
 * Model filenames can be configured via instrumentation arguments:
 * - modelFile: name of the .pte file
 * - tokenizerFile: name of the tokenizer file
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class GemmaImageTest {

    companion object {
        private const val TAG = "GemmaImageTest"
        private const val RESPONSE_TAG = "GEMMA_RESPONSE"

        // Cat test image from HuggingFace
        private const val CAT_IMAGE_URL =
            "https://huggingface.co/datasets/huggingface/documentation-images/resolve/main/transformers/tasks/cat.jpg"

        // Default model files for Gemma
        private const val DEFAULT_MODEL_FILE = "gemma.pte"
        private const val DEFAULT_TOKENIZER_FILE = "gemma.model"

        // Test image filename
        private const val TEST_IMAGE_FILENAME = "gemma_test_cat.jpg"
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<WelcomeActivity>()

    private lateinit var context: Context
    private lateinit var modelFile: String
    private lateinit var tokenizerFile: String
    private var testImageFile: File? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Read model filenames from instrumentation arguments
        val args = InstrumentationRegistry.getArguments()
        modelFile = DEFAULT_MODEL_FILE
        tokenizerFile = DEFAULT_TOKENIZER_FILE
        Log.i(TAG, "Using model: $modelFile, tokenizer: $tokenizerFile")

        // Clear SharedPreferences before test
        val prefs = context.getSharedPreferences(
            context.getString(R.string.demo_pref_file_key),
            Context.MODE_PRIVATE
        )
        prefs.edit().clear().commit()

        // Initialize Espresso Intents for stubbing
        Intents.init()
    }

    @After
    fun tearDown() {
        // Release Espresso Intents
        try {
            Intents.release()
        } catch (e: Exception) {
            Log.w(TAG, "Intents.release() failed: ${e.message}")
        }

        // Delete the test image file from cache
        testImageFile?.let { file ->
            try {
                if (file.exists() && file.delete()) {
                    Log.i(TAG, "Successfully deleted test image: ${file.absolutePath}")
                } else {
                    Log.w(TAG, "Failed to delete test image: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting test image", e)
            }
        }
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
     * Downloads the cat image and saves it to app's cache directory.
     * Returns the URI of the saved image.
     * This avoids polluting the device's gallery.
     */
    private fun downloadAndSaveImageToCache(): Uri? {
        val bitmap = downloadImageFromUrl(CAT_IMAGE_URL)
        if (bitmap == null) {
            Log.e(TAG, "Failed to download cat image")
            return null
        }

        try {
            // Save to app's cache directory
            val cacheDir = context.cacheDir
            val imageFile = File(cacheDir, TEST_IMAGE_FILENAME)

            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            }

            testImageFile = imageFile
            val imageUri = Uri.fromFile(imageFile)
            Log.i(TAG, "Saved test image to cache: $imageUri")
            return imageUri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image to cache", e)
            return null
        }
    }

    /**
     * Stubs the Gallery picker to return the given URI when launched.
     */
    private fun stubGalleryPickerResult(imageUri: Uri) {
        val resultData = Intent().apply {
            data = imageUri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)

        // Stub any intent that looks like a media picker
        Intents.intending(
            IntentMatchers.hasAction(Intent.ACTION_PICK)
        ).respondWith(result)

        // Also stub the PickVisualMedia contract which uses ACTION_GET_CONTENT or photo picker
        Intents.intending(
            IntentMatchers.hasAction(Intent.ACTION_GET_CONTENT)
        ).respondWith(result)

        // Android 13+ uses the new photo picker with a different action
        Intents.intending(
            IntentMatchers.hasAction("android.provider.action.PICK_IMAGES")
        ).respondWith(result)

        Log.i(TAG, "Stubbed Gallery picker to return: $imageUri")
    }

    /**
     * Loads the Gemma model via UI.
     */
    private fun loadModel(): Boolean {
        // Click "Load local model" card
        composeTestRule.onNodeWithText("Load local model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Select a Model").fetchSemanticsNodes().isNotEmpty()
        }

        // Select model file
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Select model path").fetchSemanticsNodes().isNotEmpty()
        }

        try {
            composeTestRule.onNodeWithText(modelFile, substring = true).performClick()
        } catch (e: AssertionError) {
            Log.e(TAG, "Model file not found: $modelFile")
            return false
        }

        // Select tokenizer file
        composeTestRule.onNodeWithText("Tokenizer").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Select tokenizer path").fetchSemanticsNodes().isNotEmpty()
        }

        try {
            composeTestRule.onNodeWithText(tokenizerFile, substring = true).performClick()
        } catch (e: AssertionError) {
            Log.e(TAG, "Tokenizer file not found: $tokenizerFile")
            return false
        }

        // Click Load Model
        composeTestRule.onNodeWithText("Load Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Yes").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Yes").performClick()

        return true
    }

    /**
     * Waits for model to be loaded.
     */
    private fun waitForModelLoaded(timeoutMs: Long = 120000): Boolean {
        return try {
            var wasSuccess = false
            composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
                val successNodes = composeTestRule.onAllNodesWithText("Successfully loaded", substring = true)
                    .fetchSemanticsNodes()
                val errorNodes = composeTestRule.onAllNodesWithText("Model load failure", substring = true)
                    .fetchSemanticsNodes()
                wasSuccess = successNodes.isNotEmpty()
                successNodes.isNotEmpty() || errorNodes.isNotEmpty()
            }
            Log.i(TAG, if (wasSuccess) "Model loaded successfully" else "Model load failed")
            wasSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Model loading timed out: ${e.message}")
            false
        }
    }

    /**
     * Waits for generation to complete.
     */
    private fun waitForGenerationComplete(timeoutMs: Long = 180000): Boolean {
        return try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
                val tpsNodes = composeTestRule.onAllNodesWithText("t/s", substring = true)
                    .fetchSemanticsNodes()
                val tokpsNodes = composeTestRule.onAllNodesWithText("tok/s", substring = true)
                    .fetchSemanticsNodes()
                tpsNodes.isNotEmpty() || tokpsNodes.isNotEmpty()
            }
            Log.i(TAG, "Generation complete")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Generation timed out: ${e.message}")
            false
        }
    }

    /**
     * Gets the response text from the model.
     */
    private fun getResponseText(): String {
        val responseBuilder = StringBuilder()
        try {
            val responseNodes = composeTestRule.onAllNodesWithText("t/s", substring = true)
                .fetchSemanticsNodes()
            for (node in responseNodes) {
                val text = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .joinToString(" ") { it.text }
                if (text.isNotBlank()) {
                    responseBuilder.append(text).append(" ")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting response text: ${e.message}")
        }
        return responseBuilder.toString()
    }

    /**
     * Tests the complete Gemma image understanding workflow:
     * 1. Load Gemma model
     * 2. Download cat image from HuggingFace (saved to cache, not gallery)
     * 3. Stub Gallery picker and attach image
     * 4. Ask what's in the image
     * 5. Validate response contains "cat"
     */
    @Test
    fun testGemmaCatImageUnderstanding() {
        composeTestRule.waitForIdle()

        // Step 1: Load model
        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        val modelLoaded = waitForModelLoaded(120000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Step 2: Download and save cat image to cache (not gallery)
        val imageUri = downloadAndSaveImageToCache()
        assertNotNull("Cat image should be downloaded and saved", imageUri)

        // Step 3: Stub the Gallery picker to return our image
        stubGalleryPickerResult(imageUri!!)

        // Step 4: Wait for chat screen to be ready and open Gallery picker
        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("Add media", useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty() ||
            composeTestRule.onAllNodes(
                androidx.compose.ui.test.hasContentDescription("Add media")
            ).fetchSemanticsNodes().isNotEmpty()
        }

        // Click Add media button to show options
        composeTestRule.onNodeWithContentDescription("Add media").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Gallery").fetchSemanticsNodes().isNotEmpty()
        }

        // Click Gallery - this will trigger our stubbed intent
        composeTestRule.onNodeWithText("Gallery").performClick()

        // Wait for the image to be added (preview should show)
        // Give it time for the image to be processed
        Thread.sleep(2000)
        composeTestRule.waitForIdle()

        // Step 5: Type a message asking about the image and send
        composeTestRule.onNodeWithTag("chat_input_field").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_input_field").performTextInput("What is in this image?")

        // Wait for send button to be enabled
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            try {
                composeTestRule.onNodeWithContentDescription("Send").assertIsEnabled()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // Step 6: Wait for response
        val generationComplete = waitForGenerationComplete(180000)
        assertTrue("Generation should complete", generationComplete)

        // Step 7: Get and validate response
        val response = getResponseText()
        Log.i(RESPONSE_TAG, "Response: $response")

        val containsCat = response.contains("cat", ignoreCase = true)
        assertTrue(
            "Response should mention 'cat', but got: $response",
            containsCat
        )

        Log.i(TAG, "Gemma image test passed - response contains 'cat'")
    }
}
