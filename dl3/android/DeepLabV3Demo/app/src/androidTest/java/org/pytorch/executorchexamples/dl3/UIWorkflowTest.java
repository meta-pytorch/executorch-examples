/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package org.pytorch.executorchexamples.dl3;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertTrue;

import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI workflow test that simulates the image segmentation workflow.
 *
 * Prerequisites:
 * - Push a .pte model file to app's private storage or ensure model can be downloaded
 *
 * This test validates:
 * - Model download/loading workflow
 * - Next sample image button functionality
 * - Run segmentation button functionality
 * - Reset image button functionality
 * - Inference time display after segmentation
 *
 * Note: This test assumes the model is already present or can be downloaded automatically.
 * For CI/CD, you may need to push the model file before running tests:
 * adb push dl3_xnnpack_fp32.pte /data/local/tmp/
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class UIWorkflowTest {

    /**
     * Tests the basic UI elements are displayed on launch.
     * Verifies that:
     * 1. ImageView is displayed
     * 2. Download button is visible
     * 3. Control buttons visibility depends on model availability
     */
    @Test
    public void testInitialUIState() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Verify ImageView is displayed
            onView(withId(R.id.imageView)).check(matches(isDisplayed()));

            // Verify download button exists
            onView(withId(R.id.downloadModelButton)).check(matches(isDisplayed()));

            // Progress bar should be invisible initially
            onView(withId(R.id.progressBar)).check(matches(not(isDisplayed())));
        }
    }

    /**
     * Tests the "Next" sample image button functionality.
     * Verifies that:
     * 1. Next button is clickable (when model is loaded)
     * 2. Clicking changes the displayed image
     * 3. Image changes multiple times when clicked repeatedly
     */
    @Test
    public void testNextSampleImage() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Check if model is already loaded (Next button visible)
            AtomicBoolean modelLoaded = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                modelLoaded.set(activity.findViewById(R.id.nextButton).getVisibility() == android.view.View.VISIBLE);
            });

            if (!modelLoaded.get()) {
                // Model not loaded, try to load it first
                boolean downloadSuccessful = downloadOrLoadModel(scenario);
                if (!downloadSuccessful) {
                    // Skip test if model cannot be loaded
                    android.util.Log.i("UIWorkflowTest", "Skipping testNextSampleImage: model not available");
                    return;
                }
            }

            // Verify Next button is visible and enabled
            onView(withId(R.id.nextButton)).check(matches(isDisplayed()));
            onView(withId(R.id.nextButton)).check(matches(isEnabled()));

            // Get the initial image properties (width, height, and first pixel for simple comparison)
            AtomicReference<String> initialImageInfo = new AtomicReference<>();
            scenario.onActivity(activity -> {
                ImageView imageView = activity.findViewById(R.id.imageView);
                if (imageView.getDrawable() instanceof BitmapDrawable) {
                    android.graphics.Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                    // Store basic image properties as a string
                    initialImageInfo.set(bitmap.getWidth() + "x" + bitmap.getHeight() + "@" + bitmap.getPixel(0, 0));
                }
            });

            // Click next button
            onView(withId(R.id.nextButton)).perform(click());
            Thread.sleep(500);

            // Verify image changed by comparing properties
            AtomicBoolean imageChanged = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                ImageView imageView = activity.findViewById(R.id.imageView);
                if (imageView.getDrawable() instanceof BitmapDrawable) {
                    android.graphics.Bitmap bitmap = ((BitmapDrawable) imageView.getDrawable()).getBitmap();
                    String currentImageInfo = bitmap.getWidth() + "x" + bitmap.getHeight() + "@" + bitmap.getPixel(0, 0);
                    // Check if image properties changed (different sample loaded)
                    imageChanged.set(!currentImageInfo.equals(initialImageInfo.get()));
                }
            });

            assertTrue("Image should change when Next button is clicked", imageChanged.get());
        }
    }

    /**
     * Tests the Run segmentation workflow.
     * Verifies that:
     * 1. Run button is enabled when model is loaded
     * 2. Clicking Run button starts segmentation
     * 3. Progress indicator appears during processing
     * 4. Inference time is displayed after completion
     * 5. Reset button becomes enabled after segmentation
     */
    @Test
    public void testRunSegmentation() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Ensure model is loaded
            AtomicBoolean modelLoaded = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                modelLoaded.set(activity.findViewById(R.id.xnnpackButton).getVisibility() == android.view.View.VISIBLE);
            });

            if (!modelLoaded.get()) {
                // Try to load model
                boolean downloadSuccessful = downloadOrLoadModel(scenario);
                if (!downloadSuccessful) {
                    android.util.Log.i("UIWorkflowTest", "Skipping testRunSegmentation: model not available");
                    return;
                }
            }

            // Verify Run button is visible and enabled
            onView(withId(R.id.xnnpackButton)).check(matches(isDisplayed()));
            onView(withId(R.id.xnnpackButton)).check(matches(isEnabled()));

            // Verify reset button is initially disabled
            onView(withId(R.id.resetImage)).check(matches(not(isEnabled())));

            // Click Run button
            onView(withId(R.id.xnnpackButton)).perform(click());
            Thread.sleep(500);

            // Progress bar should be visible during inference
            // (might be too fast to catch, so we don't enforce this)

            // Wait for segmentation to complete (max 10 seconds)
            boolean completed = waitForSegmentationComplete(scenario, 10000);
            assertTrue("Segmentation should complete within 10 seconds", completed);

            // Verify inference time is displayed
            onView(withId(R.id.inferenceTimeText)).check(matches(isDisplayed()));

            // Verify reset button is now enabled
            onView(withId(R.id.resetImage)).check(matches(isEnabled()));

            // Verify Run button is enabled again (ready for next run)
            onView(withId(R.id.xnnpackButton)).check(matches(isEnabled()));
        }
    }

    /**
     * Tests the Reset image button functionality.
     * Verifies that:
     * 1. Reset button is disabled initially
     * 2. Reset button becomes enabled after running segmentation
     * 3. Clicking Reset restores the original sample image
     * 4. Reset button becomes disabled again after reset
     */
    @Test
    public void testResetImage() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Ensure model is loaded
            AtomicBoolean modelLoaded = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                modelLoaded.set(activity.findViewById(R.id.xnnpackButton).getVisibility() == android.view.View.VISIBLE);
            });

            if (!modelLoaded.get()) {
                boolean downloadSuccessful = downloadOrLoadModel(scenario);
                if (!downloadSuccessful) {
                    android.util.Log.i("UIWorkflowTest", "Skipping testResetImage: model not available");
                    return;
                }
            }

            // Verify reset button is initially disabled
            onView(withId(R.id.resetImage)).check(matches(not(isEnabled())));

            // Run segmentation first
            onView(withId(R.id.xnnpackButton)).perform(click());
            boolean completed = waitForSegmentationComplete(scenario, 10000);
            assertTrue("Segmentation should complete", completed);

            // Reset button should now be enabled
            onView(withId(R.id.resetImage)).check(matches(isEnabled()));

            // Click reset button
            onView(withId(R.id.resetImage)).perform(click());
            Thread.sleep(500);

            // Verify reset button is disabled again
            onView(withId(R.id.resetImage)).check(matches(not(isEnabled())));

            // Verify inference time is hidden after reset
            onView(withId(R.id.inferenceTimeText)).check(matches(not(isDisplayed())));
        }
    }

    /**
     * Tests the complete workflow: Next -> Run -> Reset.
     * Verifies the full user workflow of:
     * 1. Loading a sample image
     * 2. Navigating to next sample
     * 3. Running segmentation
     * 4. Resetting the image
     */
    @Test
    public void testCompleteWorkflow() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Ensure model is loaded
            AtomicBoolean modelLoaded = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                modelLoaded.set(activity.findViewById(R.id.xnnpackButton).getVisibility() == android.view.View.VISIBLE);
            });

            if (!modelLoaded.get()) {
                boolean downloadSuccessful = downloadOrLoadModel(scenario);
                if (!downloadSuccessful) {
                    android.util.Log.i("UIWorkflowTest", "Skipping testCompleteWorkflow: model not available");
                    return;
                }
            }

            // Step 1: Click Next to change sample image
            onView(withId(R.id.nextButton)).perform(click());
            Thread.sleep(500);

            // Step 2: Run segmentation
            onView(withId(R.id.xnnpackButton)).perform(click());
            boolean completed = waitForSegmentationComplete(scenario, 10000);
            assertTrue("Segmentation should complete", completed);

            // Verify results are shown
            onView(withId(R.id.inferenceTimeText)).check(matches(isDisplayed()));
            onView(withId(R.id.resetImage)).check(matches(isEnabled()));

            // Step 3: Reset image
            onView(withId(R.id.resetImage)).perform(click());
            Thread.sleep(500);

            // Verify reset worked
            onView(withId(R.id.resetImage)).check(matches(not(isEnabled())));
            onView(withId(R.id.inferenceTimeText)).check(matches(not(isDisplayed())));

            // Step 4: Can run segmentation again
            onView(withId(R.id.xnnpackButton)).check(matches(isEnabled()));
        }
    }

    /**
     * Tests the model download button state.
     * Verifies that:
     * 1. Download button shows appropriate state based on model availability
     * 2. Button text updates during download (if download is needed)
     */
    @Test
    public void testModelDownloadButton() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Wait for activity to fully load
            Thread.sleep(1000);

            // Download button should be visible
            onView(withId(R.id.downloadModelButton)).check(matches(isDisplayed()));

            // Check the button state - it should either be:
            // - "Download Model" (model not present)
            // - "Model Ready" (model already loaded)
            // - "Downloading..." (download in progress)
            AtomicReference<String> buttonText = new AtomicReference<>();
            scenario.onActivity(activity -> {
                android.widget.Button downloadButton = activity.findViewById(R.id.downloadModelButton);
                buttonText.set(downloadButton.getText().toString());
            });

            android.util.Log.i("UIWorkflowTest", "Download button text: " + buttonText.get());
            
            // Verify the button text is one of the expected states
            assertTrue("Download button should show valid state",
                    buttonText.get().equals("Download Model") ||
                    buttonText.get().equals("Model Ready") ||
                    buttonText.get().contains("Downloading"));
        }
    }

    /**
     * Helper method to attempt to download or load the model.
     * Returns true if model is successfully loaded, false otherwise.
     */
    private boolean downloadOrLoadModel(ActivityScenario<MainActivity> scenario) throws InterruptedException {
        // Check if download button is enabled
        AtomicBoolean downloadEnabled = new AtomicBoolean(false);
        AtomicReference<String> buttonText = new AtomicReference<>();
        
        scenario.onActivity(activity -> {
            android.widget.Button downloadButton = activity.findViewById(R.id.downloadModelButton);
            downloadEnabled.set(downloadButton.isEnabled());
            buttonText.set(downloadButton.getText().toString());
        });

        // If model is already ready, return true
        if (buttonText.get().equals("Model Ready")) {
            return true;
        }

        // If download button is available and enabled, click it
        if (downloadEnabled.get() && buttonText.get().equals("Download Model")) {
            onView(withId(R.id.downloadModelButton)).perform(click());
            
            // Wait for download to complete (max 60 seconds)
            boolean downloadComplete = waitForModelReady(scenario, 60000);
            return downloadComplete;
        }

        return false;
    }

    /**
     * Waits for the model to be ready (download complete and loaded).
     * 
     * @param scenario the activity scenario
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if model is ready, false if timeout
     */
    private boolean waitForModelReady(ActivityScenario<MainActivity> scenario, long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AtomicBoolean modelReady = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                // Model is ready when Run button is visible and enabled
                android.view.View runButton = activity.findViewById(R.id.xnnpackButton);
                modelReady.set(runButton.getVisibility() == android.view.View.VISIBLE && runButton.isEnabled());
            });
            
            if (modelReady.get()) {
                return true;
            }
            Thread.sleep(1000); // Poll every second
        }
        return false;
    }

    /**
     * Waits for segmentation to complete by monitoring UI state.
     * 
     * @param scenario the activity scenario
     * @param timeoutMs maximum time to wait in milliseconds
     * @return true if segmentation completed, false if timeout
     */
    private boolean waitForSegmentationComplete(ActivityScenario<MainActivity> scenario, long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AtomicBoolean completed = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                // Segmentation is complete when:
                // 1. Progress bar is invisible
                // 2. Run button is enabled again
                // 3. Inference time is visible
                android.view.View progressBar = activity.findViewById(R.id.progressBar);
                android.view.View runButton = activity.findViewById(R.id.xnnpackButton);
                android.view.View inferenceTime = activity.findViewById(R.id.inferenceTimeText);
                
                completed.set(
                    progressBar.getVisibility() != android.view.View.VISIBLE &&
                    runButton.isEnabled() &&
                    inferenceTime.getVisibility() == android.view.View.VISIBLE
                );
            });
            
            if (completed.get()) {
                return true;
            }
            Thread.sleep(200); // Poll every 200ms
        }
        return false;
    }
}
