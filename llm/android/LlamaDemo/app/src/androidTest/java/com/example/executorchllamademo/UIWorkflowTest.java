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
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.anything;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ListView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
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

    @Before
    public void clearSharedPreferences() {
        // Clear SharedPreferences before each test to ensure a clean state
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences(
                context.getString(R.string.demo_pref_file_key), Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
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
    public void testModelLoadingWorkflow() throws Exception {
        // Launch activity manually after clearing preferences
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Dismiss the "Please Select a Model" dialog that appears on first launch
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click());

            // Step 1: Click settings button to go to settings
            onView(withId(R.id.settings)).perform(click());

            // Step 2: Verify we're in settings and paths are empty by default
            onView(withId(R.id.loadModelButton)).check(matches(isDisplayed()));
            onView(withId(R.id.modelTextView)).check(matches(withText("no model selected")));
            onView(withId(R.id.tokenizerTextView)).check(matches(withText("no tokenizer selected")));

            // Step 3: Click model selection button and select model.pte
            onView(withId(R.id.modelImageButton)).perform(click());
            // Select the model file containing "model.pte"
            onData(hasToString(containsString("model.pte"))).inRoot(isDialog()).perform(click());

            // Step 4: Click tokenizer selection button and select tokenizer.model
            onView(withId(R.id.tokenizerImageButton)).perform(click());
            // Select the tokenizer file containing "tokenizer.model"
            onData(hasToString(containsString("tokenizer.model"))).inRoot(isDialog()).perform(click());

            // Step 5: Click load model button
            onView(withId(R.id.loadModelButton)).perform(click());

            // The load model confirmation dialog should appear
            // Click "OK" to confirm loading (android.R.string.yes resolves to "OK")
            onView(withText(android.R.string.yes)).inRoot(isDialog()).perform(click());
        }
    }

    /**
     * Tests sending a message and receiving a response:
     * 1. Dismiss "Please Select a Model" dialog
     * 2. Load model (same as testModelLoadingWorkflow)
     * 3. Verify we're back on MainActivity
     * 4. Type "tell me a story" in the text input
     * 5. Click send button
     * 6. Wait for response and verify messages appear in the list
     */
    @Test
    public void testSendMessageAndReceiveResponse() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Dismiss the "Please Select a Model" dialog that appears on first launch
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click());

            // --- Load model (same steps as testModelLoadingWorkflow) ---
            onView(withId(R.id.settings)).perform(click());

            // Wait for SettingsActivity to load and verify we're there
            Thread.sleep(500);
            onView(withId(R.id.loadModelButton)).check(matches(isDisplayed()));

            // Verify load button is initially disabled (no model/tokenizer selected)
            onView(withId(R.id.loadModelButton)).check(matches(not(isEnabled())));

            // Select model - choose model.pte
            onView(withId(R.id.modelImageButton)).perform(click());
            Thread.sleep(300); // Wait for dialog to appear
            onData(hasToString(containsString("model.pte"))).inRoot(isDialog()).perform(click());
            Thread.sleep(300); // Wait for dialog to dismiss and UI to update

            // Select tokenizer - choose tokenizer.model
            onView(withId(R.id.tokenizerImageButton)).perform(click());
            Thread.sleep(300); // Wait for dialog to appear
            onData(hasToString(containsString("tokenizer.model"))).inRoot(isDialog()).perform(click());
            Thread.sleep(300); // Wait for dialog to dismiss and UI to update

            // Verify load button is now enabled
            onView(withId(R.id.loadModelButton)).check(matches(isEnabled()));

            // Click load model button
            onView(withId(R.id.loadModelButton)).perform(click());
            onView(withText(android.R.string.yes)).inRoot(isDialog()).perform(click());

            // --- Wait for model to load ---
            // Poll until we see "Successfully loaded model" message in the list
            boolean modelLoaded = waitForModelLoaded(scenario, 60000); // 60 second timeout
            assertTrue("Model should be loaded successfully", modelLoaded);

            // Verify MainActivity elements are displayed
            onView(withId(R.id.editTextMessage)).check(matches(isDisplayed()));
            onView(withId(R.id.sendButton)).check(matches(isDisplayed()));
            onView(withId(R.id.messages_view)).check(matches(isDisplayed()));

            // --- Send a message ---
            // Type "tell me a story" in the text input
            onView(withId(R.id.editTextMessage)).perform(typeText("tell me a story"), ViewActions.closeSoftKeyboard());

            // Click send button
            onView(withId(R.id.sendButton)).perform(click());

            // --- Wait for response and validate ---
            // Wait 5 seconds for model to generate response
            Thread.sleep(5000);

            // Extract all messages from the list
            AtomicInteger messageCount = new AtomicInteger(0);
            AtomicReference<String> responseText = new AtomicReference<>("");
            scenario.onActivity(activity -> {
                ListView messagesView = activity.findViewById(R.id.messages_view);
                if (messagesView != null && messagesView.getAdapter() != null) {
                    messageCount.set(messagesView.getAdapter().getCount());
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < messagesView.getAdapter().getCount(); i++) {
                        Object item = messagesView.getAdapter().getItem(i);
                        if (item instanceof Message) {
                            Message message = (Message) item;
                            sb.append(message.getIsSent() ? "User: " : "Model: ");
                            sb.append(message.getText());
                            sb.append("\n\n");
                        }
                    }
                    responseText.set(sb.toString());
                }
            });

            // Write response to file for CI to pick up
            writeResponseToFile(responseText.get());

            // Should have at least 2 messages: user message + model response (or system messages)
            assertThat("Message list should contain messages", messageCount.get(), greaterThan(0));
        }
    }

    /**
     * Waits for the model to be loaded by checking for "Successfully loaded model" message.
     *
     * @param scenario the activity scenario
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if model loaded successfully, false if timeout
     */
    private boolean waitForModelLoaded(ActivityScenario<MainActivity> scenario, long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AtomicInteger foundIndex = new AtomicInteger(-1);
            scenario.onActivity(activity -> {
                ListView messagesView = activity.findViewById(R.id.messages_view);
                if (messagesView != null && messagesView.getAdapter() != null) {
                    for (int i = 0; i < messagesView.getAdapter().getCount(); i++) {
                        Object item = messagesView.getAdapter().getItem(i);
                        if (item instanceof Message) {
                            Message message = (Message) item;
                            if (message.getText().contains("Successfully loaded model")) {
                                foundIndex.set(i);
                                break;
                            }
                        }
                    }
                }
            });
            if (foundIndex.get() >= 0) {
                return true;
            }
            Thread.sleep(500); // Poll every 500ms
        }
        return false;
    }

    /**
     * Writes the model response to a file that can be pulled from the device.
     * Writes to app's external files directory which is accessible via adb.
     * Appends to the file to support multiple test cases.
     */
    private void writeResponseToFile(String response) {
        try {
            Context context = ApplicationProvider.getApplicationContext();
            // Use external files dir which is accessible without root
            File externalDir = context.getExternalFilesDir(null);
            if (externalDir != null) {
                File outputFile = new File(externalDir, "response.txt");
                // Append mode (true) to support multiple test cases
                try (FileWriter writer = new FileWriter(outputFile, true)) {
                    writer.write(response);
                }
                android.util.Log.i("UIWorkflowTest", "Response written to: " + outputFile.getAbsolutePath());
            }
        } catch (IOException e) {
            android.util.Log.e("UIWorkflowTest", "Failed to write response file", e);
        }
    }
}
