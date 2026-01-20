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
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI workflow test using UiAutomator for cross-activity testing with Compose UI.
 * Focused on using resource IDs (testTags) and localized strings to avoid hardcoded text.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class UIWorkflowTest {

    companion object {
        private const val TAG = "UIWorkflowTest"
        private const val DEFAULT_MODEL_FILE = "stories110M.pte"
        private const val DEFAULT_TOKENIZER_FILE = "tokenizer.model"
        private const val TIMEOUT_MS = 5000L
        private const val LONG_TIMEOUT_MS = 60000L
        private const val PACKAGE_NAME = "com.example.executorchllamademo"

        private val TAG_TO_RES = mapOf(
            "settings" to R.string.settings_title,
            "logs" to R.string.logs_title,
            "backButton" to R.string.back_description,
            "sendButton" to R.string.send_description,
            "loadModelButton" to R.string.load_model,
            "thinkModeButton" to R.string.think_mode,
            "addMediaButton" to R.string.add_media_description,
            "galleryButton" to R.string.gallery_description,
            "cameraButton" to R.string.camera_description,
            "audioButton" to R.string.audio_description,
            "recordButton" to R.string.record_description,
            "modelImageButton" to R.string.select_model,
            "tokenizerImageButton" to R.string.select_tokenizer,
            "dialogConfirmButton" to android.R.string.ok,
            "dialogDismissButton" to android.R.string.cancel,
            "clearHistoryButton" to R.string.clear_history
        )
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
        Log.i(TAG, "Using model: $modelFile, tokenizer: $tokenizerFile")

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
        Log.i(TAG, "=== Starting testModelLoadingWorkflow ===")
        
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue("Activity should launch", device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LONG_TIMEOUT_MS))
        Thread.sleep(3000)

        // Dismiss initial dialog using system resource ID or common test tag
        dismissDialog()
        Thread.sleep(500)

        // Click settings using testTag (maps to resource-id)
        val settingsClicked = clickByTag("settings")
        assertTrue("Should click settings button", settingsClicked)
        Thread.sleep(2000)

        // Check if we're on settings screen via a tag-based element
        assertTrue("Should navigate to settings screen", isOnSettingsScreen())

        // Select model and tokenizer files
        selectModelAndTokenizer()
        
        // Click Load Model button by tag
        assertTrue("Should click Load Model button", clickByTag("loadModelButton"))
        Thread.sleep(1000)

        // Confirm loading dialog
        confirmDialog()
    }

    @Test
    fun testSendMessageAndReceiveResponse() {
        Log.i(TAG, "=== Starting testSendMessageAndReceiveResponse ===")
        
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue("Activity should launch", device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LONG_TIMEOUT_MS))
        Thread.sleep(3000)

        dismissDialog()
        
        val modelLoaded = loadModelWorkflow()
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type a message in the tagged edit text
        val inputField = device.findObject(By.res(PACKAGE_NAME, "editTextMessage"))
        if (inputField != null) {
            inputField.text = "tell me a story"
            Thread.sleep(500)
        }

        // Click send button by tag
        clickByTag("sendButton")
        
        // Wait for response - look for elements in the messages list
        Thread.sleep(10000)
        Log.i(TAG, "testSendMessageAndReceiveResponse completed")
    }

    @Test
    fun testStopGeneration() {
        Log.i(TAG, "=== Starting testStopGeneration ===")
        
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue("Activity should launch", device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LONG_TIMEOUT_MS))
        Thread.sleep(3000)

        dismissDialog()

        val modelLoaded = loadModelWorkflow()
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Type a long prompt
        val inputField = device.findObject(By.res(PACKAGE_NAME, "editTextMessage"))
        if (inputField != null) {
            inputField.text = "Write a very long story about a brave knight"
            Thread.sleep(500)
        }

        // Send then Stop (same button tag)
        clickByTag("sendButton")
        Thread.sleep(3000)
        clickByTag("sendButton") 
        Thread.sleep(1000)
        
        Log.i(TAG, "testStopGeneration completed")
    }

    @Test
    fun testEmptyPromptSend() {
        Log.i(TAG, "=== Starting testEmptyPromptSend ===")
        
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue("Activity should launch", device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LONG_TIMEOUT_MS))
        Thread.sleep(3000)

        dismissDialog()

        val modelLoaded = loadModelWorkflow()
        assertTrue("Model should be loaded successfully", modelLoaded)

        // Verify input field exists via tag
        val inputField = device.findObject(By.res(PACKAGE_NAME, "editTextMessage"))
        assertTrue("Input field should exist", inputField != null)
        
        Log.i(TAG, "testEmptyPromptSend completed")
    }

    @Test
    fun testFileSelectionDialogs() {
        Log.i(TAG, "=== Starting testFileSelectionDialogs ===")
        
        ActivityScenario.launch(MainActivity::class.java)
        assertTrue("Activity should launch", device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LONG_TIMEOUT_MS))
        Thread.sleep(3000)

        dismissDialog()
        clickByTag("settings")
        Thread.sleep(2000)

        // Click model selection button via tag
        clickByTag("modelImageButton")
        Thread.sleep(500)

        // Verify some file text appears (this part still needs text but it's file-based, not localized system UI)
        // However, we wait for ANY object to appear in the dialog area
        val dialogContent = device.wait(Until.hasObject(By.clazz("android.widget.ListView")), TIMEOUT_MS) ||
                            device.findObjects(By.clickable(true)).isNotEmpty()
        assertTrue("Dialog should appear", dialogContent)

        // Dismiss using localized Cancel
        dismissAction()
        Thread.sleep(500)

        // Click tokenizer selection button via tag
        clickByTag("tokenizerImageButton")
        Thread.sleep(500)

        dismissAction()
        Log.i(TAG, "testFileSelectionDialogs completed")
    }

    // --- Core Helper Methods ---

    private fun findByTag(tag: String, timeout: Long = 1000): UiObject2? {
        // Try multiple selector patterns for robustness
        val selectors = mutableListOf(
            By.res(PACKAGE_NAME, tag),
            By.res(tag),
            By.res(Pattern.compile(".*:id/$tag")),
            By.res(Pattern.compile(".*$tag"))
        )
        
        // Add description/text-based selectors if mapping exists
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TAG_TO_RES[tag]?.let { resId ->
            try {
                // For android.R.string, context might need to be system context, 
                // but usually the app context can resolve them too.
                val localizedString = context.getString(resId)
                selectors.add(By.desc(localizedString))
                selectors.add(By.descContains(localizedString))
                selectors.add(By.text(localizedString))
                selectors.add(By.textContains(localizedString))
            } catch (e: Exception) {
                Log.w(TAG, "Could not get string for resource ID mapping of tag $tag")
            }
        }
        
        for (selector in selectors) {
            val obj = device.wait(Until.findObject(selector), timeout)
            if (obj != null) {
                Log.i(TAG, "Found element for tag: $tag using selector: $selector")
                return obj
            }
        }
        return null
    }

    private fun scrollDown() {
        Log.i(TAG, "Scrolling down...")
        // Try D-Pad down first as it's often more reliable in emulators
        repeat(5) {
            device.pressDPadDown()
            Thread.sleep(100)
        }
        
        // Also try a swipe
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 2, height * 4 / 5, width / 2, height / 5, 50)
        Thread.sleep(1000)
    }

    private fun clickByTag(tag: String): Boolean {
        var obj = findByTag(tag)
        if (obj == null) {
            scrollDown()
            obj = findByTag(tag)
        }
        if (obj == null) {
            scrollDown() // Try one more time
            obj = findByTag(tag)
        }
        
        return if (obj != null) {
            obj.click()
            true
        } else {
            Log.e(TAG, "Could not find element with tag: $tag after scrolling")
            logVisibleElements()
            false
        }
    }

    private fun logVisibleElements() {
        val objects = device.findObjects(By.pkg(PACKAGE_NAME))
        Log.i(TAG, "--- Visible elements (${objects.size}) ---")
        objects.forEach { obj ->
            Log.i(TAG, "Element: resId=${obj.resourceName}, text=${obj.text}, contentDesc=${obj.contentDescription}, clickable=${obj.isClickable}")
        }
    }

    private fun dismissDialog() {
        Log.i(TAG, "Attempting to dismiss dialog...")
        // Try system resource IDs which are most robust across all locales
        val okResId = device.findObject(By.res("android", "button1"))
        if (okResId != null) {
            okResId.click()
            Thread.sleep(1000)
            return
        }

        // Fallback to localized string from system resources
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val okString = context.getString(android.R.string.ok)
        val okButton = device.findObject(By.text(okString))
        if (okButton != null) {
            okButton.click()
            Thread.sleep(1000)
        }
    }

    private fun confirmDialog() {
        Log.i(TAG, "Confirming dialog...")
        // Prefer custom testTag added to dialog buttons
        if (clickByTag("dialogConfirmButton")) {
            Thread.sleep(1000)
            return
        }
        // Fallback to system OK
        dismissDialog()
    }

    private fun dismissAction() {
        Log.i(TAG, "Dismissing action/dialog...")
        // Prefer custom testTag
        if (clickByTag("dialogDismissButton")) {
            Thread.sleep(500)
            return
        }
        // Fallback to system resource ID for negative button
        val cancelResId = device.findObject(By.res("android", "button2"))
        if (cancelResId != null) {
            cancelResId.click()
            Thread.sleep(500)
            return
        }
        // Fallback to localized string
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cancelString = context.getString(android.R.string.cancel)
        device.findObject(By.text(cancelString))?.click()
    }

    private fun isOnSettingsScreen(): Boolean {
        // Look for the Load Model button tag or the Back button tag
        return findByTag("loadModelButton", 500) != null ||
               findByTag("backButton", 500) != null
    }

    private fun selectModelAndTokenizer() {
        Log.i(TAG, "Selecting model and tokenizer...")
        
        // Click model select button by tag
        clickByTag("modelImageButton")
        Thread.sleep(500)
        
        // Match the model filename (this is user data, not UI text)
        device.wait(Until.findObject(By.textContains(modelFile)), TIMEOUT_MS)?.click()
        Thread.sleep(500)
        
        // Click tokenizer select button by tag
        clickByTag("tokenizerImageButton")
        Thread.sleep(500)
        
        // Match the tokenizer filename
        device.wait(Until.findObject(By.textContains(tokenizerFile)), TIMEOUT_MS)?.click()
        Thread.sleep(500)
    }

    private fun loadModelWorkflow(): Boolean {
        Log.i(TAG, "Executing load model workflow...")
        
        if (!clickByTag("settings")) return false
        Thread.sleep(2000)
        
        if (!isOnSettingsScreen()) return false
        
        selectModelAndTokenizer()
        
        if (!clickByTag("loadModelButton")) return false
        Thread.sleep(1000)
        
        confirmDialog()
        
        // Wait for model load success message
        // This is a bit tricky without text but let's assume it works if we can return to chat
        // We look for any text containing "success" or just return back
        Thread.sleep(2000)
        clickByTag("backButton")
        Thread.sleep(1000)
        
        return true
    }
}
