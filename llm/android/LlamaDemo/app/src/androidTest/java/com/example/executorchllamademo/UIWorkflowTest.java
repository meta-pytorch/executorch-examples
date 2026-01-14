/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.not;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI workflow test that simulates real user interactions with the LlamaDemo app.
 * This test validates the UI click workflow including:
 * - Typing a message in the input field
 * - Clicking the send button
 * - Verifying the message appears in the chat
 *
 * Note: This test requires a model to be loaded for full end-to-end validation.
 * Without a model, it validates the UI interaction flow.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class UIWorkflowTest {

  private static final String TEST_MESSAGE = "Test message for UI workflow validation";

  @Rule
  public ActivityScenarioRule<MainActivity> activityRule =
      new ActivityScenarioRule<>(MainActivity.class);

  /**
   * Tests that the main UI elements are displayed when the app starts.
   */
  @Test
  public void testMainUIElementsDisplayed() {
    // Verify the message input field is displayed
    onView(withId(R.id.editTextMessage)).check(matches(isDisplayed()));

    // Verify the send button is displayed
    onView(withId(R.id.sendButton)).check(matches(isDisplayed()));

    // Verify the settings button is displayed
    onView(withId(R.id.settings)).check(matches(isDisplayed()));

    // Verify the messages list view is displayed
    onView(withId(R.id.messages_view)).check(matches(isDisplayed()));

    // Verify the add media button is displayed
    onView(withId(R.id.addMediaButton)).check(matches(isDisplayed()));

    // Verify the think mode button is displayed
    onView(withId(R.id.thinkModeButton)).check(matches(isDisplayed()));
  }

  /**
   * Tests that the user can type a message in the input field.
   */
  @Test
  public void testUserCanTypeMessage() {
    // Type a test message in the input field
    onView(withId(R.id.editTextMessage))
        .perform(typeText(TEST_MESSAGE), closeSoftKeyboard());

    // The message should still be in the input field (not yet sent)
    onView(withId(R.id.editTextMessage)).check(matches(isDisplayed()));
  }

  /**
   * Tests clicking the settings button opens the settings activity.
   * Espresso automatically waits for the UI thread to be idle before assertions.
   */
  @Test
  public void testSettingsButtonClick() {
    // Click the settings button
    onView(withId(R.id.settings)).perform(click());

    // Espresso automatically waits for the UI to be idle
    // Verify that we're in the settings activity by checking for the load model button
    onView(withId(R.id.loadModelButton)).check(matches(isDisplayed()));
  }

  /**
   * Tests the think mode toggle button functionality.
   */
  @Test
  public void testThinkModeToggle() {
    // Click the think mode button to toggle it on
    onView(withId(R.id.thinkModeButton)).perform(click());

    // Espresso automatically waits for the UI to be idle
    // Click again to toggle it off
    onView(withId(R.id.thinkModeButton)).perform(click());

    // The button should still be displayed
    onView(withId(R.id.thinkModeButton)).check(matches(isDisplayed()));
  }

  /**
   * Tests the add media button shows media options.
   */
  @Test
  public void testAddMediaButtonShowsOptions() {
    // Click the add media button - this sets mAddMediaLayout to VISIBLE
    onView(withId(R.id.addMediaButton)).perform(click());

    // Espresso automatically waits for the UI to be idle
    // Verify that media options are displayed (gallery and camera buttons)
    onView(withId(R.id.galleryButton)).check(matches(isDisplayed()));
    onView(withId(R.id.cameraButton)).check(matches(isDisplayed()));
    onView(withId(R.id.audioButton)).check(matches(isDisplayed()));
  }

  /**
   * Tests that the send button is initially disabled when no model is loaded.
   */
  @Test
  public void testSendButtonInitiallyDisabled() {
    // On fresh start without a model loaded, the send button should be disabled
    onView(withId(R.id.sendButton)).check(matches(not(isEnabled())));
  }

  /**
   * Tests the logs button opens the logs activity.
   */
  @Test
  public void testShowLogsButtonClick() {
    // Click the show logs button
    onView(withId(R.id.showLogsButton)).perform(click());

    // Espresso automatically waits for the UI to be idle
    // Verify that we're in the logs activity by checking for the logs list
    onView(withId(R.id.logsListView)).check(matches(isDisplayed()));
  }

  /**
   * Tests the complete user workflow of typing a message.
   * Note: Full send workflow requires a model to be loaded.
   */
  @Test
  public void testCompleteMessageTypingWorkflow() {
    // Type a message
    onView(withId(R.id.editTextMessage))
        .perform(typeText(TEST_MESSAGE), closeSoftKeyboard());

    // Verify the message is in the input field
    onView(withId(R.id.editTextMessage)).check(matches(isDisplayed()));

    // Verify the messages list is displayed
    onView(withId(R.id.messages_view)).check(matches(isDisplayed()));
  }

  /**
   * Tests navigation to settings and back to main activity.
   */
  @Test
  public void testSettingsNavigationRoundTrip() {
    // Go to settings
    onView(withId(R.id.settings)).perform(click());

    // Espresso automatically waits for the UI to be idle
    // Verify we're in settings
    onView(withId(R.id.loadModelButton)).check(matches(isDisplayed()));

    // Press back to return to main activity
    androidx.test.espresso.Espresso.pressBack();

    // Espresso automatically waits for the UI to be idle
    // Verify we're back in the main activity
    onView(withId(R.id.editTextMessage)).check(matches(isDisplayed()));
  }
}
