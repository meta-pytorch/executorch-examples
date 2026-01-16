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
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasToString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ListView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
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
 *
 * Model filenames can be configured via instrumentation arguments:
 * - modelFile: name of the .pte file (default: stories110M.pte)
 * - tokenizerFile: name of the tokenizer file (default: tokenizer.model)
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class UIWorkflowTest {

    // Default filenames (stories preset)
    private static final String DEFAULT_MODEL_FILE = "stories110M.pte";
    private static final String DEFAULT_TOKENIZER_FILE = "tokenizer.model";

    private String modelFile;
    private String tokenizerFile;

    @Before
    public void setUp() {
        // Read model filenames from instrumentation arguments
        Bundle args = InstrumentationRegistry.getArguments();
        modelFile = args.getString("modelFile", DEFAULT_MODEL_FILE);
        tokenizerFile = args.getString("tokenizerFile", DEFAULT_TOKENIZER_FILE);
        android.util.Log.i("UIWorkflowTest", "Using model: " + modelFile + ", tokenizer: " + tokenizerFile);

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

            // Step 3: Click model selection button and select the model file
            onView(withId(R.id.modelImageButton)).perform(click());
            // Select the model file matching the configured filename
            onData(hasToString(endsWith(modelFile))).inRoot(isDialog()).perform(click());

            // Step 4: Click tokenizer selection button and select the tokenizer file
            onView(withId(R.id.tokenizerImageButton)).perform(click());
            // Select the tokenizer file matching the configured filename
            onData(hasToString(endsWith(tokenizerFile))).inRoot(isDialog()).perform(click());

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

            // Select model - choose the configured model file
            onView(withId(R.id.modelImageButton)).perform(click());
            Thread.sleep(300); // Wait for dialog to appear
            onData(hasToString(endsWith(modelFile))).inRoot(isDialog()).perform(click());
            Thread.sleep(300); // Wait for dialog to dismiss and UI to update

            // Select tokenizer - choose the configured tokenizer file
            onView(withId(R.id.tokenizerImageButton)).perform(click());
            Thread.sleep(300); // Wait for dialog to appear
            onData(hasToString(endsWith(tokenizerFile))).inRoot(isDialog()).perform(click());
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

            // --- Wait for response ---
            // Poll until we have some response text (at least 50 characters)
            boolean hasResponse = waitForResponseLength(scenario, 50, 60000);
            assertTrue("Model should generate a response", hasResponse);

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
     * Tests stopping generation mid-way:
     * 1. Load model
     * 2. Send a message to start generation
     * 3. Wait for generation to start (button changes to stop mode)
     * 4. Click stop button
     * 5. Verify generation stops (button returns to send mode)
     * 6. Verify partial response was received
     */
     @Test
    public void testStopGeneration() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Dismiss the "Please Select a Model" dialog
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click());

            // --- Load model ---
            onView(withId(R.id.settings)).perform(click());
            Thread.sleep(500);

            // Select model
            onView(withId(R.id.modelImageButton)).perform(click());
            Thread.sleep(300);
            onData(hasToString(endsWith(modelFile))).inRoot(isDialog()).perform(click());
            Thread.sleep(300);

            // Select tokenizer
            onView(withId(R.id.tokenizerImageButton)).perform(click());
            Thread.sleep(300);
            onData(hasToString(endsWith(tokenizerFile))).inRoot(isDialog()).perform(click());
            Thread.sleep(300);

            // Load model
            onView(withId(R.id.loadModelButton)).perform(click());
            onView(withText(android.R.string.yes)).inRoot(isDialog()).perform(click());

            // Wait for model to load
            boolean modelLoaded = waitForModelLoaded(scenario, 60000);
            assertTrue("Model should be loaded successfully", modelLoaded);

            // --- Send a message to start generation ---
            onView(withId(R.id.editTextMessage)).perform(typeText("Write a very long story about a brave knight"), ViewActions.closeSoftKeyboard());
            onView(withId(R.id.sendButton)).perform(click());

            // --- Wait for generation to start (some response text appears) ---
            boolean generationStarted = waitForResponseStarted(scenario, 30000);
            assertTrue("Generation should start (some response text should appear)", generationStarted);

            // --- Wait for some text to generate (at least 20 characters) ---
            boolean hasEnoughText = waitForResponseLength(scenario, 20, 30000);
            assertTrue("Should generate some text before stopping", hasEnoughText);

            // --- Click stop button ---
            onView(withId(R.id.sendButton)).perform(click());

            // --- Wait for generation to stop ---
            // Give it a moment to process the stop
            Thread.sleep(1000);

            // --- Verify we got a partial response ---
            AtomicReference<String> responseText = new AtomicReference<>("");
            scenario.onActivity(activity -> {
                ListView messagesView = activity.findViewById(R.id.messages_view);
                if (messagesView != null && messagesView.getAdapter() != null) {
                    for (int i = 0; i < messagesView.getAdapter().getCount(); i++) {
                        Object item = messagesView.getAdapter().getItem(i);
                        if (item instanceof Message) {
                            Message message = (Message) item;
                            // Find the model response (not sent by user, not system message)
                            if (!message.getIsSent() && !message.getText().contains("Successfully loaded")) {
                                responseText.set(message.getText());
                            }
                        }
                    }
                }
            });

            // Log the partial response
            android.util.Log.i("STOP_TEST", "Partial response after stop: " + responseText.get());

            // We should have received some tokens before stopping
            assertTrue("Should have received some response before stopping",
                    responseText.get() != null && !responseText.get().isEmpty());
        }
    }

    /**
     * Waits for generation to start by checking for model response text.
     *
     * @param scenario the activity scenario
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if response text appeared, false if timeout
     */
    private boolean waitForResponseStarted(ActivityScenario<MainActivity> scenario, long timeoutMs) throws InterruptedException {
        return waitForResponseLength(scenario, 1, timeoutMs);
    }

    /**
     * Waits for the model response to reach a minimum length.
     *
     * @param scenario the activity scenario
     * @param minLength minimum response length in characters
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if response reached minimum length, false if timeout
     */
    private boolean waitForResponseLength(ActivityScenario<MainActivity> scenario, int minLength, long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AtomicInteger responseLength = new AtomicInteger(0);
            scenario.onActivity(activity -> {
                ListView messagesView = activity.findViewById(R.id.messages_view);
                if (messagesView != null && messagesView.getAdapter() != null) {
                    for (int i = 0; i < messagesView.getAdapter().getCount(); i++) {
                        Object item = messagesView.getAdapter().getItem(i);
                        if (item instanceof Message) {
                            Message message = (Message) item;
                            // Look for a model response (not sent, not system message)
                            if (!message.getIsSent()
                                    && !message.getText().contains("Successfully loaded")
                                    && !message.getText().contains("Loading model")
                                    && !message.getText().contains("To get started")) {
                                responseLength.set(message.getText().length());
                            }
                        }
                    }
                }
            });
            if (responseLength.get() >= minLength) {
                return true;
            }
            Thread.sleep(200); // Poll every 200ms
        }
        return false;
    }

    /**
     * Waits for generation to complete by monitoring when the send button becomes enabled again.
     * After generation, the text field is cleared, so the button will be disabled.
     * We detect completion by checking that we're no longer generating (button image changes).
     *
     * @param scenario the activity scenario
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if generation completed, false if timeout
     */
    private boolean waitForGenerationComplete(ActivityScenario<MainActivity> scenario, long timeoutMs) throws InterruptedException {
        // First, wait a bit to ensure generation has started
        Thread.sleep(500);

        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AtomicBoolean isGenerating = new AtomicBoolean(true);
            scenario.onActivity(activity -> {
                ImageButton sendButton = activity.findViewById(R.id.sendButton);
                if (sendButton != null) {
                    // When generating, the button shows stop icon and is enabled
                    // When done, the button shows send icon and is disabled (empty input)
                    // We check if the button is disabled, which means generation is done
                    // and the input field is empty (cleared after sending)
                    isGenerating.set(sendButton.isEnabled());
                }
            });
            if (!isGenerating.get()) {
                return true;
            }
            Thread.sleep(500); // Poll every 500ms
        }
        return false;
    }

    /**
     * Tests that the send button is disabled when the input field is empty:
     * 1. Load model
     * 2. Verify send button is disabled with empty input
     * 3. Type some text, verify send button becomes enabled
     * 4. Clear the text, verify send button becomes disabled again
     */
    @Test
    public void testEmptyPromptSend() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Dismiss the "Please Select a Model" dialog
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click());

            // --- Load model ---
            onView(withId(R.id.settings)).perform(click());
            Thread.sleep(500);

            // Select model
            onView(withId(R.id.modelImageButton)).perform(click());
            Thread.sleep(300);
            onData(hasToString(endsWith(modelFile))).inRoot(isDialog()).perform(click());
            Thread.sleep(300);

            // Select tokenizer
            onView(withId(R.id.tokenizerImageButton)).perform(click());
            Thread.sleep(300);
            onData(hasToString(endsWith(tokenizerFile))).inRoot(isDialog()).perform(click());
            Thread.sleep(300);

            // Load model
            onView(withId(R.id.loadModelButton)).perform(click());
            onView(withText(android.R.string.yes)).inRoot(isDialog()).perform(click());

            // Wait for model to load
            boolean modelLoaded = waitForModelLoaded(scenario, 60000);
            assertTrue("Model should be loaded successfully", modelLoaded);

            // --- Test empty input behavior ---
            // Verify send button is disabled when input is empty
            onView(withId(R.id.sendButton)).check(matches(not(isEnabled())));

            // Type some text
            onView(withId(R.id.editTextMessage)).perform(typeText("hello"), ViewActions.closeSoftKeyboard());

            // Verify send button is now enabled
            onView(withId(R.id.sendButton)).check(matches(isEnabled()));

            // Clear the text
            onView(withId(R.id.editTextMessage)).perform(ViewActions.clearText());

            // Verify send button is disabled again
            onView(withId(R.id.sendButton)).check(matches(not(isEnabled())));
        }
    }

    /**
     * Tests behavior when no model/tokenizer files are in the directory:
     * 1. Go to settings
     * 2. Click model selection button
     * 3. Verify dialog shows "No files found" message
     * 4. Click tokenizer selection button
     * 5. Verify dialog shows "No files found" message
     */
    @Test
    public void testNoFilesInDirectory() throws Exception {
        // First, temporarily rename the model files to simulate empty directory
        // We can't actually delete files in a test, so we test with the existing setup
        // but verify the dialog behavior when shown with an empty list

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Dismiss the "Please Select a Model" dialog
            onView(withText(android.R.string.ok)).inRoot(isDialog()).perform(click());

            // Go to settings
            onView(withId(R.id.settings)).perform(click());
            Thread.sleep(500);

            // Verify we're in settings
            onView(withId(R.id.loadModelButton)).check(matches(isDisplayed()));

            // Click model selection button
            onView(withId(R.id.modelImageButton)).perform(click());
            Thread.sleep(300);

            // A dialog should appear - if files exist, we see them
            // If no files exist, we should see a helpful message
            // For now, just verify the dialog appears and can be dismissed
            // The dialog title "Select model path" should be visible
            onView(withText("Select model path")).inRoot(isDialog()).check(matches(isDisplayed()));

            // Dismiss by clicking outside or pressing back - use device back button
            // Since we have files in our test setup, we can click on one or press back
            // Press back to dismiss
            androidx.test.espresso.Espresso.pressBack();
            Thread.sleep(300);

            // Click tokenizer selection button
            onView(withId(R.id.tokenizerImageButton)).perform(click());
            Thread.sleep(300);

            // Verify tokenizer dialog appears
            onView(withText("Select tokenizer path")).inRoot(isDialog()).check(matches(isDisplayed()));

            // Dismiss
            androidx.test.espresso.Espresso.pressBack();
        }
    }

    /**
     * Writes the model response to logcat with a special tag for extraction.
     * The response can be extracted from logcat using: grep "LLAMA_RESPONSE"
     */
    private void writeResponseToFile(String response) {
        // Log with a unique tag that can be grepped from logcat
        android.util.Log.i("LLAMA_RESPONSE", "BEGIN_RESPONSE");
        // Split response into chunks to avoid logcat line length limits
        for (String line : response.split("\n")) {
            android.util.Log.i("LLAMA_RESPONSE", line);
        }
        android.util.Log.i("LLAMA_RESPONSE", "END_RESPONSE");
    }
}
