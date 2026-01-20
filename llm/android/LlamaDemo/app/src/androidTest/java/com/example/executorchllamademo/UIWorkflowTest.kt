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
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI workflow test using UiAutomator for cross-activity testing with Compose UI.
 *
 * Prerequisites:
 * - Push a .pte model file to /data/local/tmp/llama/
 * - Push a tokenizer file (.bin, .json, or .model) to /data/local/tmp/llama/
 *
 * Model filenames can be configured via instrumentation arguments:
 * - modelFile: name of the .pte file (default: stories110M.pte)
 * - tokenizerFile: name of the tokenizer file (default: tokenizer.model)
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class UIWorkflowTest {

    companion object {
        private const val DEFAULT_MODEL_FILE = "stories110M.pte"
        private const val DEFAULT_TOKENIZER_FILE = "tokenizer.model"
        private const val TIMEOUT_MS = 5000L
        private const val LONG_TIMEOUT_MS = 60000L
    }

    private lateinit var device: UiDevice
    private lateinit var modelFile: String
    private lateinit var tokenizerFile: String

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        // Read model filenames from instrumentation arguments
        val args = InstrumentationRegistry.getArguments()
        modelFile = args.getString("modelFile", DEFAULT_MODEL_FILE) ?: DEFAULT_MODEL_FILE
        tokenizerFile = args.getString("tokenizerFile", DEFAULT_TOKENIZER_FILE) ?: DEFAULT_TOKENIZER_FILE
        Log.i("UIWorkflowTest", "Using model: $modelFile, tokenizer: $tokenizerFile")

        // Clear SharedPreferences before each test
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(
            context.getString(R.string.demo_pref_file_key),
            Context.MODE_PRIVATE
        )
        prefs.edit().clear().commit()
    }

    /**
     * Tests the complete model loading workflow.
     */
    @Test
    fun testModelLoadingWorkflow() {
        ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(1500)

        // Dismiss the initial dialog
        dismissInitialDialog()

        // Click settings button (find by content description)
        val settingsButton = device.findObject(UiSelector().descriptionContains("Settings"))
        if (settingsButton.exists()) {
            settingsButton.click()
        } else {
            // Try finding by the test tag approach - look for clickable elements
            device.findObject(UiSelector().clickable(true).instance(1))?.click()
        }
        Thread.sleep(1000)

        // Verify we're in settings - look for "Load Model" button
        val loadModelButton = device.wait(Until.findObject(By.textContains("Load Model")), TIMEOUT_MS)
        assertTrue("Load Model button should be visible", loadModelButton != null)

        // Click model selection
        val modelSection = device.findObject(UiSelector().textContains("Model").instance(0))
        if (modelSection.exists()) {
            // Find and click the file picker button near model text
            device.findObject(UiSelector().className("android.widget.Button").instance(0))?.click()
                ?: device.findObject(UiSelector().clickable(true).instance(2))?.click()
        }
        Thread.sleep(500)

        // Select model file from dialog
        val modelFileItem = device.wait(Until.findObject(By.textContains(modelFile)), TIMEOUT_MS)
        modelFileItem?.click()
        Thread.sleep(500)

        // Click tokenizer selection
        val tokenizerButton = device.findObject(UiSelector().clickable(true).instance(3))
        tokenizerButton?.click()
        Thread.sleep(500)

        // Select tokenizer file from dialog
        val tokenizerFileItem = device.wait(Until.findObject(By.textContains(tokenizerFile)), TIMEOUT_MS)
        tokenizerFileItem?.click()
        Thread.sleep(500)

        // Click Load Model button
        val loadButton = device.findObject(UiSelector().textContains("Load Model"))
        if (loadButton.exists() && loadButton.isEnabled) {
            loadButton.click()
        }
        Thread.sleep(500)

        // Confirm dialog
        clickDialogButton("OK")
    }

    /**
     * Tests sending a message and receiving a response.
     */
    @Test
    fun testSendMessageAndReceiveResponse() {
        ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(1500)

        dismissInitialDialog()
        loadModel()

        // Wait for model to load
        val modelLoaded = waitForText("Successfully loaded model", LONG_TIMEOUT_MS)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type message in input field
        val inputField = device.findObject(UiSelector().className("android.widget.EditText"))
        if (inputField.exists()) {
            inputField.setText("tell me a story")
        }
        Thread.sleep(500)

        // Click send button
        val sendButton = device.findObject(UiSelector().descriptionContains("Send"))
        if (sendButton.exists()) {
            sendButton.click()
        } else {
            // Find clickable button near the input
            device.findObject(UiSelector().clickable(true).instance(4))?.click()
        }

        // Wait for response
        Thread.sleep(10000) // Wait for generation
        
        Log.i("TEST", "testSendMessageAndReceiveResponse completed")
    }

    /**
     * Tests stopping generation mid-way.
     */
    @Test
    fun testStopGeneration() {
        ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(1500)

        dismissInitialDialog()
        loadModel()

        val modelLoaded = waitForText("Successfully loaded model", LONG_TIMEOUT_MS)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type a long prompt
        val inputField = device.findObject(UiSelector().className("android.widget.EditText"))
        if (inputField.exists()) {
            inputField.setText("Write a very long story about a brave knight")
        }
        Thread.sleep(500)

        // Click send button
        device.findObject(UiSelector().descriptionContains("Send"))?.click()
            ?: device.findObject(UiSelector().clickable(true).instance(4))?.click()

        // Wait for generation to start
        Thread.sleep(3000)

        // Click stop button (same button, now shows stop)
        device.findObject(UiSelector().descriptionContains("Stop"))?.click()
            ?: device.findObject(UiSelector().clickable(true).instance(4))?.click()

        Thread.sleep(1000)
        Log.i("STOP_TEST", "Stop generation test completed")
    }

    /**
     * Tests empty prompt behavior.
     */
    @Test
    fun testEmptyPromptSend() {
        ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(1500)

        dismissInitialDialog()
        loadModel()

        val modelLoaded = waitForText("Successfully loaded model", LONG_TIMEOUT_MS)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Verify send button exists
        val sendButton = device.findObject(UiSelector().descriptionContains("Send"))
        assertTrue("Send button should exist", sendButton.exists() || 
            device.findObject(UiSelector().clickable(true).instance(4)).exists())

        // Type some text
        val inputField = device.findObject(UiSelector().className("android.widget.EditText"))
        if (inputField.exists()) {
            inputField.setText("hello")
            Thread.sleep(300)
            inputField.clearTextField()
        }

        Log.i("TEST", "testEmptyPromptSend completed")
    }

    /**
     * Tests file selection dialogs.
     */
    @Test
    fun testFileSelectionDialogs() {
        ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(1500)

        dismissInitialDialog()

        // Go to settings
        device.findObject(UiSelector().descriptionContains("Settings"))?.click()
            ?: device.findObject(UiSelector().clickable(true).instance(1))?.click()
        Thread.sleep(1000)

        // Click model selection to open dialog
        device.findObject(UiSelector().clickable(true).instance(2))?.click()
        Thread.sleep(500)

        // Verify dialog appears
        val dialogTitle = device.wait(Until.findObject(By.textContains("Select model")), TIMEOUT_MS)
        assertTrue("Model selection dialog should appear", dialogTitle != null)

        // Dismiss dialog
        clickDialogButton("Cancel")
        Thread.sleep(500)

        // Click tokenizer selection
        device.findObject(UiSelector().clickable(true).instance(3))?.click()
        Thread.sleep(500)

        // Verify tokenizer dialog
        val tokenizerDialog = device.wait(Until.findObject(By.textContains("Select tokenizer")), TIMEOUT_MS)
        assertTrue("Tokenizer selection dialog should appear", tokenizerDialog != null)

        clickDialogButton("Cancel")
    }

    // --- Helper Methods ---

    private fun dismissInitialDialog() {
        Thread.sleep(500)
        clickDialogButton("OK")
        Thread.sleep(300)
    }

    private fun clickDialogButton(buttonText: String) {
        val button = device.findObject(UiSelector().text(buttonText))
        if (button.exists()) {
            button.click()
        } else {
            // Try case-insensitive
            device.findObject(UiSelector().textContains(buttonText))?.click()
        }
    }

    private fun loadModel() {
        // Go to settings
        device.findObject(UiSelector().descriptionContains("Settings"))?.click()
            ?: device.findObject(UiSelector().clickable(true).instance(1))?.click()
        Thread.sleep(1000)

        // Select model file
        device.findObject(UiSelector().clickable(true).instance(2))?.click()
        Thread.sleep(500)
        device.wait(Until.findObject(By.textContains(modelFile)), TIMEOUT_MS)?.click()
        Thread.sleep(500)

        // Select tokenizer file
        device.findObject(UiSelector().clickable(true).instance(3))?.click()
        Thread.sleep(500)
        device.wait(Until.findObject(By.textContains(tokenizerFile)), TIMEOUT_MS)?.click()
        Thread.sleep(500)

        // Click load model
        device.findObject(UiSelector().textContains("Load Model"))?.click()
        Thread.sleep(500)

        // Confirm
        clickDialogButton("OK")
        Thread.sleep(500)
    }

    private fun waitForText(text: String, timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val element = device.findObject(UiSelector().textContains(text))
            if (element.exists()) {
                return true
            }
            Thread.sleep(500)
        }
        return false
    }
}
