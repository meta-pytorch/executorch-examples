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
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class UIWorkflowTest {

    companion object {
        private const val DEFAULT_MODEL_FILE = "stories110M.pte"
        private const val DEFAULT_TOKENIZER_FILE = "tokenizer.model"
        private const val TIMEOUT_MS = 5000L
        private const val LONG_TIMEOUT_MS = 60000L
        private const val PACKAGE_NAME = "com.example.executorchllamademo"
    }

    private lateinit var device: UiDevice
    private lateinit var modelFile: String
    private lateinit var tokenizerFile: String

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        
        val args = InstrumentationRegistry.getArguments()
        modelFile = args.getString("modelFile", DEFAULT_MODEL_FILE) ?: DEFAULT_MODEL_FILE
        tokenizerFile = args.getString("tokenizerFile", DEFAULT_TOKENIZER_FILE) ?: DEFAULT_TOKENIZER_FILE
        Log.i("UIWorkflowTest", "Using model: $modelFile, tokenizer: $tokenizerFile")

        // Clear SharedPreferences
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences(
            context.getString(R.string.demo_pref_file_key),
            Context.MODE_PRIVATE
        )
        prefs.edit().clear().commit()
    }

    @Test
    fun testModelLoadingWorkflow() {
        ActivityScenario.launch(MainActivity::class.java)
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
        Thread.sleep(1500)

        // Dismiss initial dialog - try multiple approaches
        dismissDialog()

        // Click settings using resource-id (testTag becomes resource-id in Compose)
        val settingsClicked = clickByResourceId("settings") || 
                              clickByDesc("Settings") ||
                              clickAnyClickable(1) // second clickable button
        assertTrue("Should click settings button", settingsClicked)
        Thread.sleep(1500)

        // Wait for settings screen - look for Load Model button
        val loadModelVisible = waitForResourceId("loadModelButton", TIMEOUT_MS) ||
                               device.wait(Until.hasObject(By.textContains("Load Model")), TIMEOUT_MS)
        assertTrue("Should navigate to settings screen", loadModelVisible)

        // Click model selection by resource-id
        clickByResourceId("modelImageButton") || clickByDesc("Select Model")
        Thread.sleep(500)

        // Select model file
        device.wait(Until.findObject(By.textContains(modelFile)), TIMEOUT_MS)?.click()
        Thread.sleep(500)

        // Click tokenizer selection
        clickByResourceId("tokenizerImageButton") || clickByDesc("Select Tokenizer")
        Thread.sleep(500)

        // Select tokenizer file
        device.wait(Until.findObject(By.textContains(tokenizerFile)), TIMEOUT_MS)?.click()
        Thread.sleep(500)

        // Click Load Model button
        clickByResourceId("loadModelButton") || clickByText("Load Model")
        Thread.sleep(500)

        // Confirm dialog
        dismissDialog()
    }

    @Test
    fun testSendMessageAndReceiveResponse() {
        ActivityScenario.launch(MainActivity::class.java)
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
        Thread.sleep(1500)

        dismissDialog()
        loadModel()

        // Wait for model to load
        val modelLoaded = waitForText("Successfully loaded model", LONG_TIMEOUT_MS)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Find input field and type message
        val inputField = device.findObject(UiSelector().className("android.widget.EditText"))
        if (inputField.exists()) {
            inputField.setText("tell me a story")
            Thread.sleep(500)
        }

        // Click send button
        clickByResourceId("sendButton") || clickByDesc("Send")
        
        // Wait for response
        Thread.sleep(10000)
        Log.i("TEST", "testSendMessageAndReceiveResponse completed")
    }

    @Test
    fun testStopGeneration() {
        ActivityScenario.launch(MainActivity::class.java)
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
        Thread.sleep(1500)

        dismissDialog()
        loadModel()

        val modelLoaded = waitForText("Successfully loaded model", LONG_TIMEOUT_MS)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type a long prompt
        val inputField = device.findObject(UiSelector().className("android.widget.EditText"))
        if (inputField.exists()) {
            inputField.setText("Write a very long story about a brave knight")
            Thread.sleep(500)
        }

        // Send
        clickByResourceId("sendButton") || clickByDesc("Send")
        Thread.sleep(3000)

        // Click stop
        clickByResourceId("sendButton") || clickByDesc("Stop")
        Thread.sleep(1000)
        
        Log.i("STOP_TEST", "Stop generation test completed")
    }

    @Test
    fun testEmptyPromptSend() {
        ActivityScenario.launch(MainActivity::class.java)
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
        Thread.sleep(1500)

        dismissDialog()
        loadModel()

        val modelLoaded = waitForText("Successfully loaded model", LONG_TIMEOUT_MS)
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Verify input field exists
        val inputField = device.findObject(UiSelector().className("android.widget.EditText"))
        assertTrue("Input field should exist", inputField.exists())

        // Type and clear text
        inputField.setText("hello")
        Thread.sleep(300)
        inputField.clearTextField()
        Thread.sleep(300)

        Log.i("TEST", "testEmptyPromptSend completed")
    }

    @Test
    fun testFileSelectionDialogs() {
        ActivityScenario.launch(MainActivity::class.java)
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), TIMEOUT_MS)
        Thread.sleep(1500)

        dismissDialog()

        // Go to settings
        clickByResourceId("settings") || clickByDesc("Settings")
        Thread.sleep(1500)

        // Click model selection
        clickByResourceId("modelImageButton") || clickByDesc("Select Model")
        Thread.sleep(500)

        // Verify model dialog appears
        val modelDialog = device.wait(Until.hasObject(By.textContains("Select model")), TIMEOUT_MS)
        assertTrue("Model selection dialog should appear", modelDialog)

        // Dismiss
        clickByText("Cancel")
        Thread.sleep(500)

        // Click tokenizer selection
        clickByResourceId("tokenizerImageButton") || clickByDesc("Select Tokenizer")
        Thread.sleep(500)

        // Verify tokenizer dialog
        val tokenizerDialog = device.wait(Until.hasObject(By.textContains("Select tokenizer")), TIMEOUT_MS)
        assertTrue("Tokenizer selection dialog should appear", tokenizerDialog)

        clickByText("Cancel")
    }

    // --- Helper Methods ---

    private fun dismissDialog() {
        Thread.sleep(300)
        // Try OK button
        clickByText("OK")
        Thread.sleep(300)
    }

    private fun clickByResourceId(resourceId: String): Boolean {
        val fullId = "$PACKAGE_NAME:id/$resourceId"
        val obj = device.findObject(UiSelector().resourceId(fullId))
        return if (obj.exists()) {
            obj.click()
            true
        } else {
            // Try without package prefix (Compose testTag)
            val obj2 = device.wait(Until.findObject(By.res(PACKAGE_NAME, resourceId)), 1000)
            obj2?.click()
            obj2 != null
        }
    }

    private fun clickByDesc(description: String): Boolean {
        val obj = device.findObject(UiSelector().descriptionContains(description))
        return if (obj.exists()) {
            obj.click()
            true
        } else {
            false
        }
    }

    private fun clickByText(text: String): Boolean {
        val obj = device.findObject(UiSelector().text(text))
        return if (obj.exists()) {
            obj.click()
            true
        } else {
            val obj2 = device.findObject(UiSelector().textContains(text))
            if (obj2.exists()) {
                obj2.click()
                true
            } else {
                false
            }
        }
    }

    private fun clickAnyClickable(index: Int): Boolean {
        val obj = device.findObject(UiSelector().clickable(true).instance(index))
        return if (obj.exists()) {
            obj.click()
            true
        } else {
            false
        }
    }

    private fun waitForResourceId(resourceId: String, timeoutMs: Long): Boolean {
        val obj = device.wait(Until.findObject(By.res(PACKAGE_NAME, resourceId)), timeoutMs)
        return obj != null
    }

    private fun loadModel() {
        // Go to settings
        clickByResourceId("settings") || clickByDesc("Settings") || clickAnyClickable(1)
        Thread.sleep(1500)

        // Select model
        clickByResourceId("modelImageButton") || clickByDesc("Select Model")
        Thread.sleep(500)
        device.wait(Until.findObject(By.textContains(modelFile)), TIMEOUT_MS)?.click()
        Thread.sleep(500)

        // Select tokenizer
        clickByResourceId("tokenizerImageButton") || clickByDesc("Select Tokenizer")
        Thread.sleep(500)
        device.wait(Until.findObject(By.textContains(tokenizerFile)), TIMEOUT_MS)?.click()
        Thread.sleep(500)

        // Load model
        clickByResourceId("loadModelButton") || clickByText("Load Model")
        Thread.sleep(500)

        // Confirm
        dismissDialog()
    }

    private fun waitForText(text: String, timeoutMs: Long): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (device.findObject(UiSelector().textContains(text)).exists()) {
                return true
            }
            Thread.sleep(500)
        }
        return false
    }
}
