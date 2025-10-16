/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

public enum Constants {
  public static let gemma3PromptTemplate = "<start_of_turn>user\n%@<end_of_turn>\n<start_of_turn>model"
  public static let llama3PromptTemplate =
    "<|begin_of_text|><|start_header_id|>user<|end_header_id|>\n%@<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n"
  public static let llavaPromptTemplate = "USER: %@ ASSISTANT: "
  public static let phi4PromptTemplate = "<|user|>%@<|end|><|assistant|>"
  public static let qwen3PromptTemplate = "<|im_start|>system\nYou are a helpful assistant.\n<|im_end|>\n<|im_start|>user\n%@<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n\n"
  public static let smolLm3PromptTemplate =
    "<|im_start|>system\n/no_think\n<|im_end|>\n<|im_start|>user\n%@<|im_end|>\n<|im_start|>assistant\n"
  public static let voxtralPromptTemplate = "%@[/INST]"
}
