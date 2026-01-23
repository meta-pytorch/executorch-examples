/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.content.Context
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Preset model sanity test that validates the preset model download and chat workflow.
 *
 * This test validates:
 * 1. Navigate from Welcome screen to Preset model screen
 * 2. Select Stories 110M and download it
 * 3. After download completes, tap to load and enter chat view
 * 4. Type "Once upon a time" and generate a response
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PresetSanityTest {

    companion object {
        private const val TAG = "PresetSanityTest"
        private const val RESPONSE_TAG = "LLAMA_RESPONSE"
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<WelcomeActivity>()

    @Before
    fun setUp() {
        // Clear SharedPreferences before test to ensure a clean state
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(
            context.getString(R.string.demo_pref_file_key),
            Context.MODE_PRIVATE
        )
        prefs.edit().clear().commit()
    }

    /**
     * Types text into the chat input field using testTag.
     */
    private fun typeInChatInput(text: String) {
        composeTestRule.onNodeWithTag("chat_input_field").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_input_field").performTextInput(text)
        composeTestRule.waitForIdle()
    }

    /**
     * Waits for generation to complete by checking for tokens-per-second metrics.
     */
    private fun waitForGenerationComplete(timeoutMs: Long = 120000): Boolean {
        return try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
                val tpsNodes = composeTestRule.onAllNodesWithText("t/s", substring = true)
                    .fetchSemanticsNodes()
                val tokpsNodes = composeTestRule.onAllNodesWithText("tok/s", substring = true)
                    .fetchSemanticsNodes()
                tpsNodes.isNotEmpty() || tokpsNodes.isNotEmpty()
            }
            Log.i(TAG, "Generation complete - found generation metrics")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Generation timed out after ${timeoutMs}ms")
            false
        }
    }

    /**
     * Waits for the model to be loaded by checking for success or error messages.
     */
    private fun waitForModelLoaded(timeoutMs: Long = 60000): Boolean {
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
            if (wasSuccess) {
                Log.i(TAG, "Model loaded successfully")
            } else {
                Log.e(TAG, "Model load failed")
            }
            wasSuccess
        } catch (e: Exception) {
            Log.e(TAG, "Model loading timed out after ${timeoutMs}ms: ${e.message}")
            false
        }
    }

    /**
     * Verifies that the model generated a non-empty response.
     */
    private fun assertModelResponseNotEmpty(timeoutMs: Long = 10000) {
        try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
                val tpsNodes = composeTestRule.onAllNodesWithText("t/s", substring = true)
                    .fetchSemanticsNodes()
                val tokpsNodes = composeTestRule.onAllNodesWithText("tok/s", substring = true)
                    .fetchSemanticsNodes()
                tpsNodes.isNotEmpty() || tokpsNodes.isNotEmpty()
            }
            Log.i(TAG, "Model response verified - found generation metrics")
        } catch (e: Exception) {
            throw AssertionError("Model response appears to be empty - no generation metrics found after ${timeoutMs}ms")
        }
    }

    /**
     * Logs the model response text for CI output.
     */
    private fun logModelResponse() {
        try {
            Log.i(RESPONSE_TAG, "BEGIN_RESPONSE")
            val responseNodes = composeTestRule.onAllNodesWithText("t/s", substring = true)
                .fetchSemanticsNodes()
            for (node in responseNodes) {
                val text = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }
                    .joinToString(" ") { it.text }
                if (text.isNotBlank()) {
                    Log.i(RESPONSE_TAG, text)
                }
            }
            Log.i(RESPONSE_TAG, "END_RESPONSE")
        } catch (e: Exception) {
            Log.d(TAG, "Could not log model response: ${e.message}")
        }
    }

    /**
     * Tests the complete preset model download and chat workflow:
     * 1. From Welcome screen, tap "Preset model" card
     * 2. Find Stories 110M and tap Download
     * 3. Wait for download to complete
     * 4. Tap the card to load model and enter chat
     * 5. Type "Once upon a time" and send
     * 6. Verify response is generated
     */
    @Ignore("Temporarily disabled")
    @Test
    fun testPresetModelDownloadAndChat() {
        composeTestRule.waitForIdle()

        // Step 1: From Welcome screen, tap "Preset model" card
        Log.i(TAG, "Step 1: Navigating to Preset model screen")
        composeTestRule.onNodeWithText("Preset model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Download Preset Model").fetchSemanticsNodes().isNotEmpty()
        }

        // Step 2: Find Stories 110M and tap Download
        Log.i(TAG, "Step 2: Finding Stories 110M and starting download")
        composeTestRule.onNodeWithText("Stories 110M").assertExists()

        // Check if already downloaded (Ready to use) or needs download
        val readyNodes = composeTestRule.onAllNodesWithText("Ready to use", substring = true)
            .fetchSemanticsNodes()

        if (readyNodes.isEmpty()) {
            // Need to download - click Download button
            composeTestRule.onNodeWithText("Download").performClick()

            // Step 3: Wait for download to complete (may take a while for large files)
            Log.i(TAG, "Step 3: Waiting for download to complete")
            composeTestRule.waitUntil(timeoutMillis = 300000) { // 5 minutes timeout for download
                composeTestRule.onAllNodesWithText("Ready to use", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            Log.i(TAG, "Download completed")
        } else {
            Log.i(TAG, "Model already downloaded, skipping download step")
        }

        // Step 4: Tap the card to load model and enter chat
        Log.i(TAG, "Step 4: Tapping card to load model")
        composeTestRule.onNodeWithText("Stories 110M").performClick()

        // Wait for Activity transition - MainActivity needs time to launch and set content
        // The SelectPresetModelActivity calls finish() after starting MainActivity
        Thread.sleep(2000)

        // Wait for model to load and chat screen to appear
        Log.i(TAG, "Waiting for model to load")
        val modelLoaded = waitForModelLoaded(90000)
        assertTrue("Model should be loaded successfully", modelLoaded)
        Log.i(TAG, "Model loaded successfully")

        // Step 5: Type "Once upon a time" and send
        Log.i(TAG, "Step 5: Typing prompt and sending")
        typeInChatInput("Once upon a time")

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

        // Step 6: Wait for generation to complete and verify response
        Log.i(TAG, "Step 6: Waiting for generation to complete")
        val generationComplete = waitForGenerationComplete(120000)
        assertTrue("Generation should complete", generationComplete)

        assertModelResponseNotEmpty()
        logModelResponse()

        Log.i(TAG, "Preset model sanity test completed successfully")
    }
}
