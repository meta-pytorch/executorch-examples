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

DEFINE_string(model1, "qwen3_06B_lora.pte",
              "First model, a PTE file.");
DEFINE_string(model2, "qwen3_06B.pte",
              "Second model, a PTE file.");
DEFINE_string(weights1, "qwen3_06B.ptd,qwen3_06B_lora.ptd",
              "Comma-separated weights for model1.");
DEFINE_string(weights2, "qwen3_06B.ptd",
              "Comma-separated weights for model2.");

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
          "<|endoftext|>", "<|im_start|>", "<|im_end|>"});
  // pad the rest of the special tokens with reserved tokens
  ssize_t reserved_special_token_num = 0;
  while (special_tokens->size() < kSpecialTokensSize) {
    special_tokens->emplace_back("<|reserved_special_token_" +
                                 std::to_string(reserved_special_token_num++) +
                                 "|>");
  }
  return special_tokens;
}

// Parse comma-separated string into vector of strings
static std::vector<std::string> parse_data_paths(const std::string& paths) {
  std::vector<std::string> result;
  std::stringstream ss(paths);
  std::string item;
  while (std::getline(ss, item, ',')) {
    if (!item.empty()) {
      result.push_back(item);
    }
  }
  return result;
}
} // namespace

int main(int argc, char *argv[]) {
  ET_LOG(Info, "Running program-data separation lora example...");

  gflags::ParseCommandLineFlags(&argc, &argv, true);

  const char *model1 = FLAGS_model1.c_str();
  const char *model2 = FLAGS_model2.c_str();

  const char *tokenizer_path = FLAGS_tokenizer_path.c_str();
  const char *prompt = FLAGS_prompt.c_str();
  float temperature = FLAGS_temperature;
  int32_t seq_len = FLAGS_seq_len;
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

  // Create runners with parsed data paths.
  std::vector<std::string> data_files1 = parse_data_paths(FLAGS_weights1);
  std::vector<std::string> data_files2 = parse_data_paths(FLAGS_weights2);

  ET_LOG(Info, "Loading model1: %s with weights: %s", model1, FLAGS_weights1.c_str());
  std::unique_ptr<llm::TextLLMRunner> runner1 =
      llm::create_text_llm_runner(model1, std::move(tokenizer1),
                                  data_files1, temperature);

  ET_LOG(Info, "Loading model2: %s with weights: %s", model2, FLAGS_weights2.c_str());
  std::unique_ptr<llm::TextLLMRunner> runner2 =
      llm::create_text_llm_runner(model2, std::move(tokenizer2),
                                  data_files2, temperature);

  llm::GenerationConfig config{
      .echo = false,
      .seq_len = seq_len,
      .temperature = temperature};

  std::string formatted_prompt = std::string();
  if (FLAGS_apply_chat_template) {
    ET_LOG(Info, "Applying chat template...");
    // Qwen3 chat template format
    formatted_prompt += "<|im_start|>user\n";
    formatted_prompt += prompt;
    formatted_prompt += "<|im_end|>\n<|im_start|>assistant\n";
  } else {
    formatted_prompt += prompt;
  }

  ET_LOG(Info, "Generating with model %s...", model1);
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
