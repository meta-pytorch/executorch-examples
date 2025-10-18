/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

public enum ModelType {
  GEMMA_3,
  LLAMA_3,
  LLAVA_1_5,
  LLAMA_GUARD_3,
  QWEN_3,
  VOXTRAL;

  @Override
  public String toString() {
    // Replace underscores with spaces, capitalize words, and handle special cases
    String pretty = name().replace('_', ' ').toLowerCase();
    // Capitalize the first letter if needed
    if (!pretty.isEmpty()) {
      pretty = pretty.substring(0, 1).toUpperCase() + pretty.substring(1);
    }
    // Optionally, handle special capitalization
    pretty = pretty.replace("Llava 1 5", "LLaVA 1.5");
    return pretty;
  }
}
