/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

import Foundation

public enum Constants {
  public static let llama3PromptTemplate = "<|begin_of_text|><|start_header_id|>user<|end_header_id|>%@<|eot_id|><|start_header_id|>assistant<|end_header_id|>"
  public static let llavaPreamble = "A chat between a curious human and an artificial intelligence assistant. The assistant gives helpful, detailed, and polite answers to the human's questions. USER: "
  public static let llavaTextPromptTemplate = "%@ ASSISTANT: "
  public static let phi4PromptTemplate = "<|user|>%@<|end|><|assistant|>"
  public static let qwen3PromptTemplate = "<|im_start|>system\nYou are a helpful assistant.\n<|im_end|>\n<|im_start|>user\n%@<|im_end|>\n<|im_start|>assistant\n<think>\n\n</think>\n\n\n"
}
