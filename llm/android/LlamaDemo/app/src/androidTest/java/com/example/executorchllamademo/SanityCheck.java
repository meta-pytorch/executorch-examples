/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pytorch.executorch.extension.llm.LlmCallback;
import org.pytorch.executorch.extension.llm.LlmModule;

/**
 * Sanity check test for model loading and generation.
 *
 * Model filenames can be configured via instrumentation arguments:
 * - modelFile: name of the .pte file (default: stories110M.pte)
 * - tokenizerFile: name of the tokenizer file (default: tokenizer.model)
 */
@RunWith(AndroidJUnit4.class)
public class SanityCheck implements LlmCallback {

  private static final String RESOURCE_PATH = "/data/local/tmp/llama/";

  // Default filenames (stories preset)
  private static final String DEFAULT_MODEL_FILE = "stories110M.pte";
  private static final String DEFAULT_TOKENIZER_FILE = "tokenizer.model";

  private String modelFile;
  private String tokenizerFile;

  private final List<String> results = new ArrayList<>();

  @Before
  public void setUp() {
    // Read model filenames from instrumentation arguments
    Bundle args = InstrumentationRegistry.getArguments();
    modelFile = args.getString("modelFile", DEFAULT_MODEL_FILE);
    tokenizerFile = args.getString("tokenizerFile", DEFAULT_TOKENIZER_FILE);
    android.util.Log.i("SanityCheck", "Using model: " + modelFile + ", tokenizer: " + tokenizerFile);
  }

  @Test
  public void testLoadAndGenerate() {
    String tokenizerPath = RESOURCE_PATH + tokenizerFile;
    File model = new File(RESOURCE_PATH + modelFile);
    LlmModule mModule = new LlmModule(model.getPath(), tokenizerPath, 0.8f);

    int loadResult = mModule.load();
    // Check that the model can be loaded successfully
    assertEquals(0, loadResult);

    // Run a testing prompt
    mModule.generate("How do you do! I'm testing llm on mobile device", SanityCheck.this);

    // Verify we got some response
    assertFalse("Should receive at least one result token", results.isEmpty());
  }

  @Override
  public void onResult(String result) {
    results.add(result);
  }

  @Override
  public void onStats(String result) {
    // Not measuring performance for now
  }
}
