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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Preset model sanity test that validates the preset model UI workflow.
 *
 * This test validates:
 * 1. Navigate from Welcome screen to Preset model screen
 * 2. Load preset config from URL
 * 3. Verify Stories 110M model is displayed
 *
 * Note: Download and chat steps are skipped in CI due to network constraints.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class PresetSanityTest {

    companion object {
        private const val TAG = "PresetSanityTest"
        private const val DEFAULT_CONFIG_URL = "https://raw.githubusercontent.com/meta-pytorch/executorch-examples/615fa601fd75493ab0e1828c14bbd24f83cd5133/llm/android/LlamaDemo/app/src/main/assets/preset_models.json"
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

        // Also clear the preset config preferences
        val configPrefs = context.getSharedPreferences("preset_config_prefs", Context.MODE_PRIVATE)
        configPrefs.edit().clear().commit()
    }

    /**
     * Loads the preset config from URL.
     * This is needed because the bundled preset_models.json is empty by default.
     */
    private fun loadPresetConfigFromUrl() {
        Log.i(TAG, "Loading preset config from URL")

        // Type the URL into the config URL field (it's empty by default)
        composeTestRule.onNodeWithTag("config_url_field").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("config_url_field").performTextInput(DEFAULT_CONFIG_URL)

        // Small delay to ensure text is entered
        Thread.sleep(500)

        // Click the Load button
        composeTestRule.onNodeWithText("Load").performClick()

        // Wait for config to load (models should appear)
        // Don't use waitForIdle here as the loading spinner animation keeps Compose busy
        composeTestRule.waitUntil(timeoutMillis = 60000) {
            composeTestRule.onAllNodesWithText("Stories 110M").fetchSemanticsNodes().isNotEmpty()
        }
        Log.i(TAG, "Preset config loaded successfully")
    }

    /**
     * Tests the preset model UI workflow:
     * 1. From Welcome screen, tap "Preset model" card
     * 2. Load preset config from URL (since bundled JSON is empty)
     * 3. Verify Stories 110M model is displayed
     *
     * Note: Download and chat steps are skipped in CI due to network constraints.
     */
    @Test
    fun testPresetModelDownloadAndChat() {
        composeTestRule.waitForIdle()

        // Step 1: From Welcome screen, tap "Preset model" card
        Log.i(TAG, "Step 1: Navigating to Preset model screen")
        composeTestRule.onNodeWithText("Preset model").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Download Preset Model").fetchSemanticsNodes().isNotEmpty()
        }

        // Step 2: Load preset config from URL
        Log.i(TAG, "Step 2: Loading preset config from URL")
        loadPresetConfigFromUrl()

        // Step 3: Find Stories 110M and verify it exists
        Log.i(TAG, "Step 3: Verifying Stories 110M is displayed")
        composeTestRule.onNodeWithText("Stories 110M").assertExists()
        Log.i(TAG, "Stories 110M found - preset model screen is working correctly")

        // Note: Download and chat steps are skipped in CI due to network constraints
    }
}
