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
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * UI workflow tests for the Compose-based DL3 demo.
 * 
 * Tests include:
 * - Download button functionality with and without model present
 * - Model run/segmentation testing
 * - UI state management (Next, Reset buttons)
 * - Inference time display
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class UIWorkflowTest {

    companion object {
        private const val MODEL_FILENAME = "dl3_xnnpack_fp32.pte"
        private const val MODEL_BACKUP_FILENAME = "dl3_xnnpack_fp32.pte.backup"
        private const val TAG = "UIWorkflowTest"
        private const val DOWNLOAD_TIMEOUT_MS = 120000L // 2 minutes
        private const val INFERENCE_TIMEOUT_MS = 30000L // 30 seconds
    }

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var modelPath: String
    private lateinit var backupPath: String

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        modelPath = "${context.filesDir.absolutePath}/$MODEL_FILENAME"
        backupPath = "${context.filesDir.absolutePath}/$MODEL_BACKUP_FILENAME"
        
        // Ensure model is downloaded for most tests
        ensureModelAvailable()
    }

    @After
    fun tearDown() {
        // Clean up backup file if exists
        val backupFile = File(backupPath)
        if (backupFile.exists()) {
            backupFile.delete()
        }
    }

    /**
     * Ensures model is available by downloading if needed.
     */
    private fun ensureModelAvailable() {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.i(TAG, "Model not found, downloading...")
            // Click download button and wait for completion
            composeTestRule.onNodeWithTag("downloadButton").assertExists()
            composeTestRule.onNodeWithTag("downloadButton").performClick()
            
            // Wait for download to complete (button should disappear)
            composeTestRule.waitUntil(timeoutMillis = DOWNLOAD_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithTag("downloadButton")
                    .fetchSemanticsNodes().isEmpty()
            }
        } else {
            Log.i(TAG, "Model already available at $modelPath")
        }
    }

    /**
     * Tests that the initial UI is displayed correctly when model is ready.
     */
    @Test
    fun testInitialUIWithModel() {
        // Model buttons should be visible
        composeTestRule.onNodeWithTag("nextButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("pickButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("runButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("resetButton").assertIsDisplayed()
        
        // Image should be displayed
        composeTestRule.onNodeWithTag("segmentationImage").assertExists()
        
        // Download button should not be visible
        composeTestRule.onNodeWithTag("downloadButton").assertDoesNotExist()
        
        // Reset button should be disabled initially
        composeTestRule.onNodeWithTag("resetButton").assertIsNotEnabled()
    }

    /**
     * Tests the download button functionality when model is not present.
     * Uses rename-test-restore pattern to simulate missing model.
     */
    @Test
    fun testDownloadButtonWhenModelMissing() {
        val modelFile = File(modelPath)
        val backupFile = File(backupPath)
        
        // Step 1: Rename existing model to backup
        if (modelFile.exists()) {
            modelFile.renameTo(backupFile)
            Log.i(TAG, "Renamed model to backup")
        }
        
        try {
            // Step 2: Restart activity to show download button
            composeTestRule.activityRule.scenario.recreate()
            
            // Wait for UI to settle
            Thread.sleep(1000)
            
            // Download button should be visible
            composeTestRule.onNodeWithTag("downloadButton").assertIsDisplayed()
            composeTestRule.onNodeWithTag("downloadButton").assertIsEnabled()
            
            // Model buttons should not be visible
            composeTestRule.onNodeWithTag("nextButton").assertDoesNotExist()
            composeTestRule.onNodeWithTag("runButton").assertDoesNotExist()
            
            // Step 3: Click download button
            composeTestRule.onNodeWithTag("downloadButton").performClick()
            
            // Progress indicator should appear
            composeTestRule.onNodeWithTag("progressIndicator").assertExists()
            
            // Step 4: Wait for download to complete
            composeTestRule.waitUntil(timeoutMillis = DOWNLOAD_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithTag("downloadButton")
                    .fetchSemanticsNodes().isEmpty()
            }
            
            // Model buttons should now be visible
            composeTestRule.onNodeWithTag("nextButton").assertIsDisplayed()
            composeTestRule.onNodeWithTag("runButton").assertIsDisplayed()
            
            // Verify new model file exists
            assert(modelFile.exists()) { "Model should be downloaded" }
            
        } finally {
            // Step 5: Restore backup if test downloaded new model and backup exists
            if (backupFile.exists()) {
                if (modelFile.exists()) {
                    modelFile.delete()
                }
                backupFile.renameTo(modelFile)
                Log.i(TAG, "Restored model from backup")
            }
        }
    }

    /**
     * Tests the "Next" button functionality to cycle through sample images.
     */
    @Test
    fun testNextButtonCyclesSamples() {
        // Click Next button
        composeTestRule.onNodeWithTag("nextButton").performClick()
        
        // Wait for image to change
        Thread.sleep(500)
        
        // Image should still be displayed
        composeTestRule.onNodeWithTag("segmentationImage").assertExists()
        
        // Can click Next again
        composeTestRule.onNodeWithTag("nextButton").performClick()
        Thread.sleep(500)
        composeTestRule.onNodeWithTag("segmentationImage").assertExists()
    }

    /**
     * Tests running segmentation and verifying inference time display.
     */
    @Test
    fun testRunSegmentation() {
        // Run button should be enabled
        composeTestRule.onNodeWithTag("runButton").assertIsEnabled()
        
        // Click Run button
        composeTestRule.onNodeWithTag("runButton").performClick()
        
        // Progress indicator should appear briefly
        // (might be too fast to catch consistently)
        
        // Wait for inference to complete
        composeTestRule.waitUntil(timeoutMillis = INFERENCE_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithTag("inferenceTime")
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Inference time should be displayed
        composeTestRule.onNodeWithTag("inferenceTime").assertIsDisplayed()
        
        // Reset button should now be enabled
        composeTestRule.onNodeWithTag("resetButton").assertIsEnabled()
        
        // Run button should still be enabled for next run
        composeTestRule.onNodeWithTag("runButton").assertIsEnabled()
    }

    /**
     * Tests the Reset button functionality.
     */
    @Test
    fun testResetButton() {
        // First run segmentation to enable reset
        composeTestRule.onNodeWithTag("runButton").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = INFERENCE_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithTag("inferenceTime")
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Reset button should be enabled
        composeTestRule.onNodeWithTag("resetButton").assertIsEnabled()
        
        // Click Reset button
        composeTestRule.onNodeWithTag("resetButton").performClick()
        
        // Wait for reset to complete
        Thread.sleep(500)
        
        // Reset button should be disabled again
        composeTestRule.onNodeWithTag("resetButton").assertIsNotEnabled()
        
        // Inference time should disappear
        composeTestRule.onNodeWithTag("inferenceTime").assertDoesNotExist()
    }

    /**
     * Tests the complete workflow: Next -> Run -> Reset.
     */
    @Test
    fun testCompleteWorkflow() {
        // Step 1: Click Next to change sample
        composeTestRule.onNodeWithTag("nextButton").performClick()
        Thread.sleep(500)
        
        // Step 2: Run segmentation
        composeTestRule.onNodeWithTag("runButton").performClick()
        
        composeTestRule.waitUntil(timeoutMillis = INFERENCE_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithTag("inferenceTime")
                .fetchSemanticsNodes().isNotEmpty()
        }
        
        // Verify results are shown
        composeTestRule.onNodeWithTag("inferenceTime").assertIsDisplayed()
        composeTestRule.onNodeWithTag("resetButton").assertIsEnabled()
        
        // Step 3: Reset image
        composeTestRule.onNodeWithTag("resetButton").performClick()
        Thread.sleep(500)
        
        // Verify reset worked
        composeTestRule.onNodeWithTag("resetButton").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("inferenceTime").assertDoesNotExist()
        
        // Step 4: Can run segmentation again
        composeTestRule.onNodeWithTag("runButton").assertIsEnabled()
    }

    /**
     * Tests multiple consecutive runs to ensure model can be reused.
     * 
     * Note: Disabled due to known issue.
     */
    @Ignore("Known issue - test not working")
    @Test
    fun testMultipleConsecutiveRuns() {
        for (i in 1..3) {
            Log.i(TAG, "Running segmentation iteration $i")
            
            // Run segmentation
            composeTestRule.onNodeWithTag("runButton").performClick()
            
            composeTestRule.waitUntil(timeoutMillis = INFERENCE_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithTag("inferenceTime")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            
            // Verify inference time is displayed
            composeTestRule.onNodeWithTag("inferenceTime").assertIsDisplayed()
            
            // Reset for next iteration (except last)
            if (i < 3) {
                composeTestRule.onNodeWithTag("resetButton").performClick()
                Thread.sleep(500)
            }
        }
    }
}
