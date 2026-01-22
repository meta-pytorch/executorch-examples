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
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.intent.Intents.intending
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
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
        private const val RESPONSE_TAG = "LLAMA_RESPONSE"
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
     * Clears chat history via the Settings UI.
     * This ensures each test starts with a clean state.
     */
    private fun clearChatHistory() {
        composeTestRule.waitForIdle()

        // Go to settings
        try {
            composeTestRule.onNodeWithContentDescription("Settings").performClick()
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule.onAllNodesWithText("Clear Chat History")
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not open settings to clear history: ${e.message}")
            return
        }

        // Click Clear Chat History button (clears immediately, no confirmation dialog)
        try {
            composeTestRule.onNodeWithText("Clear Chat History").performClick()
            composeTestRule.waitForIdle()
            Log.i(TAG, "Chat history cleared")
        } catch (e: Exception) {
            Log.d(TAG, "Could not clear chat history: ${e.message}")
        }

        // Go back to chat screen using system back
        try {
            Espresso.pressBack()
            composeTestRule.waitForIdle()
        } catch (e: Exception) {
            Log.d(TAG, "Could not press back after clearing history: ${e.message}")
        }
    }

    /**
     * Navigates to settings and selects model/tokenizer files.
     * Returns true if successful.
     */
    private fun loadModel(): Boolean {
        // Click settings button
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5001) {
            composeTestRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }

        // Click model row to open model selection dialog
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5002) {
            composeTestRule.onAllNodesWithText("Select model path").fetchSemanticsNodes().isNotEmpty()
        }

        // Select the model file
        try {
            composeTestRule.onNodeWithText(modelFile, substring = true).performClick()
        } catch (e: AssertionError) {
            Log.e(TAG, "Model file not found: $modelFile")
            return false
        }
        composeTestRule.waitForIdle()

        // Click tokenizer row to open tokenizer selection dialog
        composeTestRule.onNodeWithText("Tokenizer").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5003) {
            composeTestRule.onAllNodesWithText("Select tokenizer path").fetchSemanticsNodes().isNotEmpty()
        }

        // Select the tokenizer file
        try {
            composeTestRule.onNodeWithText(tokenizerFile, substring = true).performClick()
        } catch (e: AssertionError) {
            Log.e(TAG, "Tokenizer file not found: $tokenizerFile")
            return false
        }
        composeTestRule.waitForIdle()

        // Click Load Model button
        composeTestRule.onNodeWithText("Load Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5004) {
            composeTestRule.onAllNodesWithText("Yes").fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm in dialog
        composeTestRule.onNodeWithText("Yes").performClick()
        composeTestRule.waitForIdle()

        return true
    }

    /**
     * Waits for the model to be loaded by checking for success or error messages.
     * Uses Compose's waitUntil for proper synchronization.
     */
    private fun waitForModelLoaded(timeoutMs: Long = 60000): Boolean {
        return try {
            composeTestRule.waitUntil(timeoutMillis = timeoutMs) {
                val successNodes = composeTestRule.onAllNodesWithText("Successfully loaded", substring = true)
                    .fetchSemanticsNodes()
                val errorNodes = composeTestRule.onAllNodesWithText("Model Load failure", substring = true)
                    .fetchSemanticsNodes()
                successNodes.isNotEmpty() || errorNodes.isNotEmpty()
            }
            // Check which one appeared
            val successNodes = composeTestRule.onAllNodesWithText("Successfully loaded", substring = true)
                .fetchSemanticsNodes()
            if (successNodes.isNotEmpty()) {
                Log.i(TAG, "Model loaded successfully")
                true
            } else {
                Log.e(TAG, "Model load failed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model loading timed out after ${timeoutMs}ms")
            false
        }
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
     * Clears the chat input field.
     */
    private fun clearChatInput() {
        composeTestRule.onNodeWithTag("chat_input_field").performTextClearance()
        composeTestRule.waitForIdle()
    }

    /**
     * Verifies that the model generated a non-empty response by checking for
     * tokens per second metrics (e.g., "t/s") which only appear in model responses.
     * Uses Compose's waitUntil for proper synchronization.
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
     * Searches for text nodes containing generation metrics (t/s) and extracts the response.
     */
    private fun logModelResponse() {
        try {
            Log.i(RESPONSE_TAG, "BEGIN_RESPONSE")
            // Find all nodes with t/s metrics - these are model response bubbles
            val responseNodes = composeTestRule.onAllNodesWithText("t/s", substring = true)
                .fetchSemanticsNodes()
            for (node in responseNodes) {
                // Get text from the semantics node
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
     * Tests the complete model loading workflow:
     * 1. Click settings button
     * 2. Verify model path and tokenizer path show default "no selection" text
     * 3. Click model selection, select model.pte
     * 4. Click tokenizer selection, select tokenizer.model
     * 5. Click load model button
     */
    @Test
    fun testModelLoadingWorkflow() {
        composeTestRule.waitForIdle()

        // Click settings button
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5005) {
            composeTestRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify we're in settings
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Load Model").assertIsDisplayed()
        composeTestRule.onNodeWithText("no model selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("no tokenizer selected").assertIsDisplayed()

        // Click model selection
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5006) {
            composeTestRule.onAllNodesWithText("Select model path").fetchSemanticsNodes().isNotEmpty()
        }

        // Select model file
        composeTestRule.onNodeWithText(modelFile, substring = true).performClick()
        composeTestRule.waitForIdle()

        // Click tokenizer selection
        composeTestRule.onNodeWithText("Tokenizer").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5007) {
            composeTestRule.onAllNodesWithText("Select tokenizer path").fetchSemanticsNodes().isNotEmpty()
        }

        // Select tokenizer file
        composeTestRule.onNodeWithText(tokenizerFile, substring = true).performClick()
        composeTestRule.waitForIdle()

        // Click load model button
        composeTestRule.onNodeWithText("Load Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5008) {
            composeTestRule.onAllNodesWithText("Yes").fetchSemanticsNodes().isNotEmpty()
        }

        // Confirm loading
        composeTestRule.onNodeWithText("Yes").performClick()
    }

    /**
     * Tests sending a message and receiving a response.
     */
    @Test
    fun testSendMessageAndReceiveResponse() {
        composeTestRule.waitForIdle()

        // Clear chat history first to ensure clean state
        clearChatHistory()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        // Wait for model to load
        val modelLoaded = waitForModelLoaded(90000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type a message using testTag
        typeInChatInput("tell me a story")

        // Click send
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // Wait for generation to complete
        val generationComplete = waitForGenerationComplete()
        assertTrue("Generation should complete", generationComplete)

        // Verify model generated a non-empty response
        assertModelResponseNotEmpty()

        // Log response for CI workflow summary
        logModelResponse()

        Log.i(TAG, "Send message and receive response test completed successfully")
    }

    /**
     * Tests stopping generation mid-way.
     */
    @Test
    fun testStopGeneration() {
        composeTestRule.waitForIdle()

        // Clear chat history first to ensure clean state
        clearChatHistory()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        val modelLoaded = waitForModelLoaded(90000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type a long prompt using testTag
        typeInChatInput("Write a very long story about a brave knight who goes on an adventure")

        // Click send
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // Wait for Stop button to appear (generation started)
        try {
            composeTestRule.waitUntil(timeoutMillis = 5009) {
                composeTestRule.onAllNodes(hasContentDescription("Stop"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            // Click stop
            composeTestRule.onNodeWithContentDescription("Stop").performClick()
        } catch (e: Exception) {
            // Generation might have already finished
            Log.i(TAG, "Stop button not found - generation may have completed")
        }

        composeTestRule.waitForIdle()

        // Wait for generation to fully stop
        waitForGenerationComplete(30000)

        // Verify that some response was generated (even if stopped early)
        assertModelResponseNotEmpty()

        Log.i(TAG, "Stop generation test completed successfully")
    }

    /**
     * Tests that send button is disabled when input is empty.
     */
    @Test
    fun testEmptyPromptSend() {
        composeTestRule.waitForIdle()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        val modelLoaded = waitForModelLoaded(90000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Wait for send button to be in expected state (disabled with empty input)
        composeTestRule.waitUntil(timeoutMillis = 5010) {
            composeTestRule.onAllNodes(hasContentDescription("Send"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify send button is disabled with empty input
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()

        // Type some text using testTag
        typeInChatInput("hello")

        // Wait for send button to become enabled
        composeTestRule.waitUntil(timeoutMillis = 2001) {
            try {
                composeTestRule.onNodeWithContentDescription("Send").assertIsEnabled()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // Verify send button is now enabled
        composeTestRule.onNodeWithContentDescription("Send").assertIsEnabled()

        // Clear the text
        clearChatInput()

        // Wait for send button to become disabled
        composeTestRule.waitUntil(timeoutMillis = 2002) {
            try {
                composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // Verify send button is disabled again
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

    /**
     * Tests file selection dialog behavior.
     */
    @Test
    fun testNoFilesInDirectory() {
        composeTestRule.waitForIdle()

        // Go to settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5011) {
            composeTestRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify settings screen
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        // Click model selection
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5012) {
            composeTestRule.onAllNodesWithText("Select model path").fetchSemanticsNodes().isNotEmpty()
        }

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

        // Go to settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5013) {
            composeTestRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify initial state
        composeTestRule.onNodeWithText("no model selected").assertIsDisplayed()
        composeTestRule.onNodeWithText("no tokenizer selected").assertIsDisplayed()

        // Select a model first
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5014) {
            composeTestRule.onAllNodesWithText("Select model path").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(modelFile, substring = true).performClick()

        // Wait for dialog to close and model to be selected
        composeTestRule.waitUntil(timeoutMillis = 5015) {
            composeTestRule.onAllNodesWithText(modelFile, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify model is selected
        composeTestRule.onNodeWithText(modelFile, substring = true).assertIsDisplayed()

        // Open model selection again
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5016) {
            composeTestRule.onAllNodesWithText("Select model path").fetchSemanticsNodes().isNotEmpty()
        }

        // Cancel
        composeTestRule.onNodeWithText("Cancel").performClick()

        // Wait for dialog to close
        composeTestRule.waitUntil(timeoutMillis = 5017) {
            composeTestRule.onAllNodesWithText("Select model path").fetchSemanticsNodes().isEmpty()
        }

        // Verify selection is preserved
        composeTestRule.onNodeWithText(modelFile, substring = true).assertIsDisplayed()
    }

    /**
     * Tests that load button is disabled until both model and tokenizer are selected.
     */
    @Test
    fun testLoadButtonDisabledState() {
        composeTestRule.waitForIdle()

        // Go to settings
        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5018) {
            composeTestRule.onAllNodesWithText("Settings").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify load button is initially disabled
        composeTestRule.onNodeWithText("Load Model").assertIsNotEnabled()

        // Select only model
        composeTestRule.onNodeWithText("Model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5019) {
            composeTestRule.onAllNodesWithText("Select model path").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(modelFile, substring = true).performClick()

        // Wait for dialog to close
        composeTestRule.waitUntil(timeoutMillis = 5020) {
            composeTestRule.onAllNodesWithText("Select model path").fetchSemanticsNodes().isEmpty()
        }

        // Verify load button still disabled (no tokenizer)
        composeTestRule.onNodeWithText("Load Model").assertIsNotEnabled()

        // Select tokenizer
        composeTestRule.onNodeWithText("Tokenizer").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5021) {
            composeTestRule.onAllNodesWithText("Select tokenizer path").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText(tokenizerFile, substring = true).performClick()

        // Wait for dialog to close
        composeTestRule.waitUntil(timeoutMillis = 5022) {
            composeTestRule.onAllNodesWithText("Select tokenizer path").fetchSemanticsNodes().isEmpty()
        }

        // Verify load button is now enabled
        composeTestRule.onNodeWithText("Load Model").assertIsEnabled()
    }

    /**
     * Tests whitespace-only prompt handling.
     */
    @Test
    fun testWhitespaceOnlyPrompt() {
        composeTestRule.waitForIdle()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        val modelLoaded = waitForModelLoaded(90000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Wait for send button to appear
        composeTestRule.waitUntil(timeoutMillis = 5023) {
            composeTestRule.onAllNodes(hasContentDescription("Send"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Verify send button is disabled with empty input
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()

        // Type only spaces using testTag
        typeInChatInput("     ")

        // Verify send button is still disabled (whitespace only)
        composeTestRule.onNodeWithContentDescription("Send").assertIsNotEnabled()

        // Clear and type actual text
        clearChatInput()
        typeInChatInput("hello")

        // Wait for send button to become enabled
        composeTestRule.waitUntil(timeoutMillis = 2003) {
            try {
                composeTestRule.onNodeWithContentDescription("Send").assertIsEnabled()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        // Verify send button is now enabled
        composeTestRule.onNodeWithContentDescription("Send").assertIsEnabled()
    }

    /**
     * Tests sending multiple messages and verifying conversation flow:
     * 1. Load model
     * 2. Send first message and wait for response
     * 3. Send second message and wait for response
     * 4. Verify both user messages and responses are visible
     */
    @Ignore("Temporarily disabled")
    @Test
    fun testMultipleMessagesConversation() {
        composeTestRule.waitForIdle()

        // Clear chat history first to ensure clean state
        clearChatHistory()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        val modelLoaded = waitForModelLoaded(90000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // --- Send first message ---
        // Use unique message unlikely to appear in model response
        val firstMessage = "XYZTEST123"
        typeInChatInput(firstMessage)
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // Wait for first response to complete
        val firstResponseComplete = waitForGenerationComplete(120000)
        assertTrue("First response should complete", firstResponseComplete)

        // Verify first user message is visible and model response is not empty
        composeTestRule.onNodeWithText(firstMessage, substring = true).assertExists()
        assertModelResponseNotEmpty()

        // --- Send second message ---
        val secondMessage = "ABCTEST456"
        typeInChatInput(secondMessage)

        // Wait for send button to be enabled
        composeTestRule.waitUntil(timeoutMillis = 5024) {
            try {
                composeTestRule.onNodeWithContentDescription("Send").assertIsEnabled()
                true
            } catch (e: AssertionError) {
                false
            }
        }

        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // Wait for second response to complete
        val secondResponseComplete = waitForGenerationComplete(120001)
        assertTrue("Second response should complete", secondResponseComplete)

        // Verify both user messages are visible in conversation
        composeTestRule.onNodeWithText(firstMessage, substring = true).assertExists()
        composeTestRule.onNodeWithText(secondMessage, substring = true).assertExists()

        // Verify model responses are not empty (should have multiple t/s metrics now)
        assertModelResponseNotEmpty()

        Log.i(TAG, "Multiple messages conversation test completed successfully")
    }

    /**
     * Waits for generation to complete by checking for tokens-per-second metrics
     * which appear when generation finishes.
     * Uses Compose's waitUntil for proper synchronization.
     */
    private fun waitForGenerationComplete(timeoutMs: Long = 120000): Boolean {
        // Wait for generation metrics to appear (indicates generation completed)
        // We check for "t/s" or "tok/s" which only appear after generation finishes
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
     * Tests the add media button toggle functionality.
     */
    @Test
    fun testCollapseMediaButton() {
        composeTestRule.waitForIdle()

        // Verify add media button is present
        try {
            composeTestRule.onNodeWithContentDescription("Add media").assertIsDisplayed()

            // Click add media button to show options
            composeTestRule.onNodeWithContentDescription("Add media").performClick()

            // Wait for media options to appear
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule.onAllNodesWithText("Gallery").fetchSemanticsNodes().isNotEmpty()
            }

            // Verify media options appear (Gallery, Camera, Audio)
            composeTestRule.onNodeWithText("Gallery").assertIsDisplayed()
            composeTestRule.onNodeWithText("Camera").assertIsDisplayed()

            // Click collapse to hide
            composeTestRule.onNodeWithContentDescription("Collapse media").performClick()

            // Wait for media options to disappear
            composeTestRule.waitUntil(timeoutMillis = 3000) {
                composeTestRule.onAllNodesWithText("Gallery").fetchSemanticsNodes().isEmpty()
            }

            // Verify media options are hidden
            composeTestRule.onNodeWithText("Gallery").assertDoesNotExist()
        } catch (e: AssertionError) {
            // Media buttons might not be visible depending on backend type
            Log.i(TAG, "Media buttons not present - might be MediaTek backend")
        }
    }

    /**
     * Tests multimodal (LLaVA) workflow with image input:
     * 1. Load model
     * 2. Select a photo from gallery
     * 3. Ask "what is in the photo"
     * 4. Wait for response with 120 second timeout
     *
     * Prerequisites:
     * - Push a test image to /data/local/tmp/llama/test_image.jpg
     * - Use LLaVA model preset (llava.pte, tokenizer.bin)
     */
    @Test
    fun testMultimodalImageInput() {
        composeTestRule.waitForIdle()

        // Clear chat history first to ensure clean state
        clearChatHistory()

        val loaded = loadModel()
        assertTrue("Model should be selected successfully", loaded)

        // Wait for model to load with extended timeout for LLaVA
        val modelLoaded = waitForModelLoaded(90000)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Wait for UI to settle
        composeTestRule.waitForIdle()

        // Initialize Intents for stubbing the gallery picker
        Intents.init()
        try {
            // Create a test image URI - use a content URI pointing to a test image
            // The test image should be pushed to device before running the test
            val testImageUri = Uri.parse("file:///data/local/tmp/llama/test_image.jpg")

            // Create result Intent with the test image URI
            val resultData = Intent().apply {
                data = testImageUri
                clipData = android.content.ClipData.newUri(
                    composeTestRule.activity.contentResolver,
                    "Test Image",
                    testImageUri
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val result = Instrumentation.ActivityResult(Activity.RESULT_OK, resultData)

            // Stub any intent that looks like a media picker
            intending(IntentMatchers.hasAction(MediaStore.ACTION_PICK_IMAGES)).respondWith(result)
            intending(IntentMatchers.hasAction(Intent.ACTION_GET_CONTENT)).respondWith(result)
            intending(IntentMatchers.hasAction(Intent.ACTION_PICK)).respondWith(result)

            // Click add media button to show options
            try {
                composeTestRule.onNodeWithContentDescription("Add media").assertIsDisplayed()
                composeTestRule.onNodeWithContentDescription("Add media").performClick()

                // Wait for media options to appear
                composeTestRule.waitUntil(timeoutMillis = 5000) {
                    composeTestRule.onAllNodesWithText("Gallery").fetchSemanticsNodes().isNotEmpty()
                }

                // Click Gallery to select image
                composeTestRule.onNodeWithText("Gallery").performClick()
                composeTestRule.waitForIdle()

                // Wait a moment for the image to be processed
                Thread.sleep(2000)
            } catch (e: AssertionError) {
                Log.e(TAG, "Media buttons not available - this test requires a multimodal model")
                Intents.release()
                return
            }

        } finally {
            Intents.release()
        }

        // Type the question about the image
        typeInChatInput("what is in the photo")

        // Click send
        composeTestRule.onNodeWithContentDescription("Send").performClick()
        composeTestRule.waitForIdle()

        // Wait for generation to complete with extended 120 second timeout for LLaVA
        val generationComplete = waitForGenerationComplete(120000)
        assertTrue("Generation should complete within 120 seconds", generationComplete)

        // Verify model generated a non-empty response
        assertModelResponseNotEmpty()

        // Log response for CI workflow summary
        logModelResponse()

        Log.i(TAG, "Multimodal image input test completed successfully")
    }
}
