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
import java.util.Arrays;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pytorch.executorch.extension.llm.LlmCallback;
import org.pytorch.executorch.extension.llm.LlmModule;

@RunWith(AndroidJUnit4.class)
public class PerfTest implements LlmCallback {

  private static final String RESOURCE_PATH = "/data/local/tmp/llama/";
  private static final String TOKENIZER_BIN = "tokenizer.json";

  private final List<String> results = new ArrayList<>();
  private final List<Float> tokensPerSecond = new ArrayList<>();
    @Test public void testSanity() {}


  @Override
  public void onResult(String result) {
    results.add(result);
  }

  @Override
  public void onStats(String result) {
    try {
      JSONObject jsonObject = new JSONObject(result);
      int numGeneratedTokens = jsonObject.getInt("generated_tokens");
      int inferenceEndMs = jsonObject.getInt("inference_end_ms");
      int promptEvalEndMs = jsonObject.getInt("prompt_eval_end_ms");
      float tps = (float) numGeneratedTokens / (inferenceEndMs - promptEvalEndMs) * 1000;
      tokensPerSecond.add(tps);
    } catch (JSONException e) {
    }
  }

  private void report(final String metric, final Float value) {
    Bundle bundle = new Bundle();
    bundle.putFloat(metric, value);
    InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle);
  }

  private void report(final String key, final String value) {
    Bundle bundle = new Bundle();
    bundle.putString(key, value);
    InstrumentationRegistry.getInstrumentation().sendStatus(0, bundle);
  }
}
