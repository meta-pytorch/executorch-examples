/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 * @lint-ignore-every CLANGTIDY facebook-hte-Deprecated
 */

#include <memory>
#include <string>
#include <vector>

#include <gflags/gflags.h>

#include <executorch/extension/llm/runner/llm_runner_helper.h>
#include <executorch/extension/llm/runner/stats.h>
#include <executorch/extension/llm/runner/text_llm_runner.h>
#include <executorch/extension/llm/runner/text_prefiller.h>
#include <executorch/extension/llm/runner/text_token_generator.h>
#include <pytorch/tokenizers/hf_tokenizer.h>
#if defined(ET_USE_THREADPOOL)
#include <executorch/extension/threadpool/cpuinfo_utils.h>
#include <executorch/extension/threadpool/threadpool.h>
#endif

DEFINE_string(model1, "llama_3_2_1B_lora.pte",
              "First model, a PTE file.");
DEFINE_string(model2, "llama_3_2_1B.pte",
              "Second model, a PTE file.");
DEFINE_string(weights, "foundation.ptd",
              "Shared weights, a PTD file.");

DEFINE_string(tokenizer_path, "tokenizer.model", "Tokenizer.");

DEFINE_string(prompt, "What is the meaning of life?", "Prompt.");

DEFINE_double(temperature, 0,
              "Temperature; Default is 0. 0 = greedy argmax sampling "
              "(deterministic). Lower temperature = more deterministic");

DEFINE_int32(
    seq_len, 128,
    "Total number of tokens to generate (prompt + output). Defaults to "
    "max_seq_len. If the number of input tokens + seq_len > max_seq_len, the "
    "output will be truncated to max_seq_len tokens.");

DEFINE_bool(
  apply_chat_template, false,
  "Apply a LLAMA-style chat template to the prompt. Defaults to false.");

using executorch::extension::Module;
using executorch::runtime::Error;
namespace llm = executorch::extension::llm;

namespace {
static constexpr int32_t kSpecialTokensSize = 256;
static inline std::unique_ptr<std::vector<std::string>>
_get_default_special_tokens() {
  auto special_tokens =
      std::make_unique<std::vector<std::string>>(std::vector<std::string>{
          "<|begin_of_text|>", "<|end_of_text|>",
          "<|reserved_special_token_0|>", "<|reserved_special_token_1|>",
          "<|finetune_right_pad_id|>", "<|step_id|>", "<|start_header_id|>",
          "<|end_header_id|>", "<|eom_id|>", "<|eot_id|>", "<|python_tag|>"});
  // pad the rest of the special tokens with reserved tokens
  ssize_t reserved_special_token_num = 2;
  while (special_tokens->size() < kSpecialTokensSize) {
    special_tokens->emplace_back("<|reserved_special_token_" +
                                 std::to_string(reserved_special_token_num++) +
                                 "|>");
  }
  return special_tokens;
}
} // namespace

int main(int argc, char *argv[]) {
  ET_LOG(Info, "Running program-data separation lora example...");

  gflags::ParseCommandLineFlags(&argc, &argv, true);

  const char *model1 = FLAGS_model1.c_str();
  const char *model2 = FLAGS_model2.c_str();
  const char *weights = FLAGS_weights.c_str();

  const char *tokenizer_path = FLAGS_tokenizer_path.c_str();
  const char *prompt = FLAGS_prompt.c_str();
  float temperature = FLAGS_temperature;
  int32_t seq_len = 128;
  int32_t cpu_threads = -1;

  // Create tokenizers.
  std::unique_ptr<tokenizers::Tokenizer> tokenizer1 =
      llm::load_tokenizer(tokenizer_path, _get_default_special_tokens());
  std::unique_ptr<tokenizers::Tokenizer> tokenizer2 =
      llm::load_tokenizer(tokenizer_path, _get_default_special_tokens());

  if (tokenizer1 == nullptr || tokenizer2 == nullptr) {
    ET_LOG(Info,
           "Failed to load %s as a Tiktoken, Sentencepiece, Llama2.c or HFTokenizer "
           "tokenizer, make sure the artifact is one of these types",
           tokenizer_path);
    return 1;
  }

  // Create runners.
  std::unique_ptr<llm::TextLLMRunner> runner1 =
      llm::create_text_llm_runner(model1, std::move(tokenizer1),
                                  weights, temperature);
  std::unique_ptr<llm::TextLLMRunner> runner2 =
      llm::create_text_llm_runner(model1, std::move(tokenizer2),
                                  weights, temperature);

  llm::GenerationConfig config{
      .echo = false,
      .seq_len = seq_len,
      .temperature = temperature};

  std::string formatted_prompt = std::string();
  if (FLAGS_apply_chat_template) {
    // System Prompt.
    formatted_prompt += "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n";
    formatted_prompt += "You are a helpful assistant.<|eot_id|>";
    // User prompt.
    formatted_prompt += "<|start_header_id|>user<|end_header_id|>\n";
    formatted_prompt += prompt;
    formatted_prompt += "<|eot_id|><|start_header_id|>assistant<|end_header_id|>";
  } else {
    formatted_prompt += prompt;
  }

  ET_LOG(Info, "Generating with model %s...", model1);
  ET_LOG(Info, "Formatted prompt: %s", formatted_prompt.c_str());
  auto error = runner1->generate(formatted_prompt, config);
  if (error != Error::Ok) {
    ET_LOG(Error, "Failed to generate with model %s, error code %zu.",
           model1, error);
    return 1;
  }

  ET_LOG(Info, "Generating with model %s...", model2);
  error = runner2->generate(formatted_prompt, config);
  if (error != Error::Ok) {
    ET_LOG(Error, "Failed to generate with model %s, error code %zu.",
           model2, error);
    return 1;
  }

  return 0;
}
