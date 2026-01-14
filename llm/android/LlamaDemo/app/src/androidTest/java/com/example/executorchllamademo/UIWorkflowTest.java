/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI workflow test that simulates the model loading workflow.
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
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class UIWorkflowTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    /**
     * Tests the complete model loading workflow:
     * 1. Click settings button
     * 2. Verify model path and tokenizer path show default "no selection" text
     * 3. Click model selection, select the first entry from dialog
     * 4. Click tokenizer selection, select the first entry from dialog
     * 5. Click load model button
     */
    @Test
    public void testModelLoadingWorkflow() {
        // Step 1: Click settings button to go to settings
        onView(withId(R.id.settings)).perform(click());

        // Step 2: Verify we're in settings and paths are empty by default
        onView(withId(R.id.loadModelButton)).check(matches(isDisplayed()));
        onView(withId(R.id.modelTextView)).check(matches(withText("no model selected")));
        onView(withId(R.id.tokenizerTextView)).check(matches(withText("no tokenizer selected")));

        // Step 3: Click model selection button and select the first entry
        onView(withId(R.id.modelImageButton)).perform(click());
        // Select the first item in the dialog list
        onData(anything()).atPosition(0).perform(click());

        // Step 4: Click tokenizer selection button and select the first entry
        onView(withId(R.id.tokenizerImageButton)).perform(click());
        // Select the first item in the dialog list
        onData(anything()).atPosition(0).perform(click());

        // Step 5: Click load model button
        onView(withId(R.id.loadModelButton)).perform(click());

        // The load model confirmation dialog should appear
        // Click "Yes" to confirm loading
        onView(withText("Yes")).perform(click());
    }
}