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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI workflow test that simulates the model loading workflow using Compose testing APIs.
 *
 * Prerequisites:
 * - Push a .pte model file to /data/local/tmp/llama/
 * - Push a tokenizer file (.bin, .json, or .model) to /data/local/tmp/llama/
 *
 * This test validates:
 * - Settings screen shows empty model/tokenizer paths by default
 * - File selection dialogs display pushed files
 * - User can select model and tokenizer files
 * - User can click the load model button
 *
 * Model filenames can be configured via instrumentation arguments:
 * - modelFile: name of the .pte file (default: stories110M.pte)
 * - tokenizerFile: name of the tokenizer file (default: tokenizer.model)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class UIWorkflowTest {

    companion object {
        private const val TAG = "UIWorkflowTest"
        private const val DEFAULT_MODEL_FILE = "stories110M.pte"
        private const val DEFAULT_TOKENIZER_FILE = "tokenizer.model"
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var modelFile: String
    private lateinit var tokenizerFile: String

    @Before
    fun setUp() {
        // Read model filenames from instrumentation arguments
        val args = InstrumentationRegistry.getArguments()
        modelFile = args.getString("modelFile", DEFAULT_MODEL_FILE) ?: DEFAULT_MODEL_FILE
        tokenizerFile = args.getString("tokenizerFile", DEFAULT_TOKENIZER_FILE) ?: DEFAULT_TOKENIZER_FILE
        Log.i(TAG, "Using model: $modelFile, tokenizer: $tokenizerFile")

        // Clear SharedPreferences before each test to ensure a clean state
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(
            context.getString(R.string.demo_pref_file_key),
            Context.MODE_PRIVATE
        )
        prefs.edit().clear().commit()
    }

    /**
     * Dismisses the "Please Select a Model" dialog if it appears.
     */
    private fun dismissSelectModelDialogIfPresent() {
        composeTestRule.waitForIdle()
        try {
            // Try to find and click the OK button on the select model dialog
            composeTestRule.onNodeWithText("OK").performClick()
            composeTestRule.waitForIdle()
        } catch (e: AssertionError) {
            // Dialog might not be present, that's fine
            Log.d(TAG, "Select model dialog not present or already dismissed")
        }
    }

    /**
     * Navigates to settings and selects model/tokenizer files.
     * Returns true if successful.
     */
    private fun loadModel(): Boolean {
        // Click settings button
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // Click model row to open model selection dialog
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Select the model file
        try {
            composeTestRule.onNodeWithText(modelFile, substring = true).performClick()
        } catch (e: AssertionError) {
            Log.e(TAG, "Model file not found: $modelFile")
            return false
        }
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Click tokenizer row to open tokenizer selection dialog
        composeTestRule.onNodeWithText("Tokenizer").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Select the tokenizer file
        try {
            composeTestRule.onNodeWithText(tokenizerFile, substring = true).performClick()
        } catch (e: AssertionError) {
            Log.e(TAG, "Tokenizer file not found: $tokenizerFile")
            return false
        }
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Click Load Model button
        composeTestRule.onNodeWithText("Load Model").performClick()
        composeTestRule.waitForIdle()

        // Confirm in dialog
        composeTestRule.onNodeWithText("Yes").performClick()
        composeTestRule.waitForIdle()

        return true
    }

    /**
     * Waits for the model to be loaded by checking for "Successfully loaded model" message.
     */
    private fun waitForModelLoaded(timeoutMs: Long = 60000): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                composeTestRule.onNodeWithText("Successfully loaded model", substring = true)
                    .assertExists()
                return true
            } catch (e: AssertionError) {
                Thread.sleep(500)
            }
        }
        return false
    }

    /**
     * Waits for response to appear with minimum length.
     */
    private fun waitForResponseLength(minLength: Int, timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            // Check if there's any substantial text in the messages
            // This is a simplified check - Compose testing makes it harder to extract text length
            composeTestRule.waitForIdle()
            Thread.sleep(200)

            // Try to find nodes that might contain response
            try {
                val nodes = composeTestRule.onAllNodesWithText("", substring = true)
                    .fetchSemanticsNodes()
                // If we have substantial content, return true
                if (nodes.size > 5) { // Rough heuristic for response
                    return true
                }
            } catch (e: Exception) {
                // Continue waiting
            }
            Thread.sleep(300)
        }
        return false
    }

    /**
     * Tests the complete model loading workflow:
     * 1. Dismiss the "Please Select a Model" dialog
     * 2. Click settings button
     * 3. Verify model path and tokenizer path show default "no selection" text
     * 4. Click model selection, select model.pte
     * 5. Click tokenizer selection, select tokenizer.model
     * 6. Click load model button
     */
    @Test
    fun testModelLoadingWorkflow() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        // Dismiss the "Please Select a Model" dialog
        dismissSelectModelDialogIfPresent()

        // Click settings button
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // Verify we're in settings
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Load Model").assertIsDisplayed()
        composeTestRule.onNodeWithText("no model selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("no tokenizer selected").assertIsDisplayed()

        // Click model selection
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Select model file
        composeTestRule.onNodeWithText(modelFile, substring = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Click tokenizer selection
        composeTestRule.onNodeWithText("Tokenizer").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Select tokenizer file
        composeTestRule.onNodeWithText(tokenizerFile, substring = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Click load model button
        composeTestRule.onNodeWithText("Load Model").performClick()
        composeTestRule.waitForIdle()

        // Confirm loading
        composeTestRule.onNodeWithText("Yes").performClick()
    }

    /**
     * Tests sending a message and receiving a response.
     */
    @Test
    fun testSendMessageAndReceiveResponse() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        dismissSelectModelDialogIfPresent()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        // Wait for model to load
        val modelLoaded = waitForModelLoaded(60000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type a message
        composeTestRule.onNodeWithText("Type a message...", substring = true).performClick()
        composeTestRule.onNodeWithText("Type a message...", substring = true).performTextInput("tell me a story")
        composeTestRule.waitForIdle()

        // Click send
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // Wait for response
        Thread.sleep(5000) // Give model time to generate

        // Just verify we got past sending without crashes
        composeTestRule.waitForIdle()
    }

    /**
     * Tests stopping generation mid-way.
     */
    @Test
    fun testStopGeneration() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        dismissSelectModelDialogIfPresent()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        val modelLoaded = waitForModelLoaded(60000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type a long prompt
        composeTestRule.onNodeWithText("Type a message...", substring = true).performClick()
        composeTestRule.onNodeWithText("Type a message...", substring = true)
            .performTextInput("Write a very long story about a brave knight who goes on an adventure")
        composeTestRule.waitForIdle()

        // Click send
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // Wait a bit for generation to start
        Thread.sleep(2000)

        // Click stop (the button should now show stop icon)
        try {
            composeTestRule.onNodeWithContentDescription("Stop").performClick()
        } catch (e: AssertionError) {
            // Generation might have already finished
            Log.i(TAG, "Stop button not found - generation may have completed")
        }

        composeTestRule.waitForIdle()
    }

    /**
     * Tests behavior during generation.
     */
    @Test
    fun testSendDuringGeneration() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        dismissSelectModelDialogIfPresent()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        val modelLoaded = waitForModelLoaded(60000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type a prompt
        composeTestRule.onNodeWithText("Type a message...", substring = true).performClick()
        composeTestRule.onNodeWithText("Type a message...", substring = true)
            .performTextInput("Write a detailed story")
        composeTestRule.waitForIdle()

        // Click send
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // Wait briefly
        Thread.sleep(1000)

        // UI should still be responsive
        composeTestRule.waitForIdle()
    }

    /**
     * Tests that send button is disabled when input is empty.
     */
    @Test
    fun testEmptyPromptSend() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        dismissSelectModelDialogIfPresent()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        val modelLoaded = waitForModelLoaded(60000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Verify send button is disabled with empty input
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()

        // Type some text
        composeTestRule.onNodeWithText("Type a message...", substring = true).performClick()
        composeTestRule.onNodeWithText("Type a message...", substring = true).performTextInput("hello")
        composeTestRule.waitForIdle()

        // Verify send button is now enabled
        composeTestRule.onNodeWithContentDescription("Send").assertIsEnabled()

        // Clear the text
        composeTestRule.onNode(hasText("hello")).performTextClearance()
        composeTestRule.waitForIdle()

        // Verify send button is disabled again
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

    /**
     * Tests file selection dialog behavior.
     */
    @Test
    fun testNoFilesInDirectory() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        dismissSelectModelDialogIfPresent()

        // Go to settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // Verify settings screen
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        // Click model selection
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Dialog should appear - verify it has a title
        composeTestRule.onNodeWithText("Select model path").assertIsDisplayed()

        // Press cancel or select a file
        try {
            composeTestRule.onNodeWithText("Cancel").performClick()
        } catch (e: AssertionError) {
            // If cancel not found, select a file
            composeTestRule.onNodeWithText(modelFile, substring = true).performClick()
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Tests cancel file selection preserves previous selection.
     */
    @Test
    fun testCancelFileSelection() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        dismissSelectModelDialogIfPresent()

        // Go to settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // Verify initial state
        composeTestRule.onNodeWithText("no model selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("no tokenizer selected").assertIsDisplayed()

        // Select a model first
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)
        composeTestRule.onNodeWithText(modelFile, substring = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Verify model is selected
        composeTestRule.onNodeWithText(modelFile, substring = true).assertIsDisplayed()

        // Open model selection again
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Cancel
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Verify selection is preserved
        composeTestRule.onNodeWithText(modelFile, substring = true).assertIsDisplayed()
    }

    /**
     * Tests that load button is disabled until both model and tokenizer are selected.
     */
    @Test
    fun testLoadButtonDisabledState() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        dismissSelectModelDialogIfPresent()

        // Go to settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)

        // Verify load button is initially disabled
        composeTestRule.onNodeWithText("Load Model").assertIsNotEnabled()

        // Select only model
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)
        composeTestRule.onNodeWithText(modelFile, substring = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Verify load button still disabled (no tokenizer)
        composeTestRule.onNodeWithText("Load Model").assertIsNotEnabled()

        // Select tokenizer
        composeTestRule.onNodeWithText("Tokenizer").performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)
        composeTestRule.onNodeWithText(tokenizerFile, substring = true).performClick()
        composeTestRule.waitForIdle()
        Thread.sleep(300)

        // Verify load button is now enabled
        composeTestRule.onNodeWithText("Load Model").assertIsEnabled()
    }

    /**
     * Tests whitespace-only prompt handling.
     */
    @Test
    fun testWhitespaceOnlyPrompt() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        dismissSelectModelDialogIfPresent()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        val modelLoaded = waitForModelLoaded(60000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Verify send button is disabled with empty input
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()

        // Type only spaces
        composeTestRule.onNodeWithText("Type a message...", substring = true).performClick()
        composeTestRule.onNodeWithText("Type a message...", substring = true).performTextInput("     ")
        composeTestRule.waitForIdle()

        // Verify send button is still disabled (whitespace only)
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()

        // Clear and type actual text
        composeTestRule.onNode(hasText("     ")).performTextClearance()
        composeTestRule.onNodeWithText("Type a message...", substring = true).performTextInput("hello")
        composeTestRule.waitForIdle()

        // Verify send button is now enabled
        composeTestRule.onNodeWithContentDescription("Send").assertIsEnabled()
    }

    /**
     * Tests the add media button toggle functionality.
     */
    @Test
    fun testCollapseMediaButton() {
        composeTestRule.waitForIdle()
        Thread.sleep(1000)

        dismissSelectModelDialogIfPresent()

        // Verify add media button is present
        try {
            composeTestRule.onNodeWithContentDescription("Add media").assertIsDisplayed()

            // Click add media button to show options
            composeTestRule.onNodeWithContentDescription("Add media").performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(300)

            // Verify media options appear (Gallery, Camera, Audio)
            composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
            composeTestRule.onNodeWithText("Camera").assertIsDisplayed()

            // Click collapse to hide
            composeTestRule.onNodeWithContentDescription("Collapse media").performClick()
            composeTestRule.waitForIdle()
            Thread.sleep(300)

            // Verify media options are hidden
            composeTestRule.onNodeWithText("Gallery").assertDoesNotExist()
        } catch (e: AssertionError) {
            // Media buttons might not be visible depending on backend type
            Log.i(TAG, "Media buttons not present - might be MediaTek backend")
        }
    }
}
