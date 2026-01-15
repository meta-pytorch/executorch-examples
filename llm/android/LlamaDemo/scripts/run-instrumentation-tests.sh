#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

# Run instrumentation tests with model preset configuration
# Usage: ./run-instrumentation-tests.sh <preset> [custom_pte_url] [custom_tokenizer_url]

set -e

MODEL_PRESET="${1:-stories}"
CUSTOM_PTE_URL="${2:-}"
CUSTOM_TOKENIZER_URL="${3:-}"

echo "=== MODEL_PRESET value: '$MODEL_PRESET' ==="

# Determine model and tokenizer filenames based on preset
case "$MODEL_PRESET" in
  llama)
    MODEL_FILE="llama3_2-1B.pte"
    TOKENIZER_FILE="tokenizer.model"
    ;;
  qwen3)
    MODEL_FILE="model.pte"
    TOKENIZER_FILE="tokenizer.json"
    ;;
  custom)
    MODEL_FILE=$(basename "$CUSTOM_PTE_URL")
    TOKENIZER_FILE=$(basename "$CUSTOM_TOKENIZER_URL")
    ;;
  *)
    MODEL_FILE="stories110M.pte"
    TOKENIZER_FILE="tokenizer.model"
    ;;
esac

echo "=== Model files: $MODEL_FILE, $TOKENIZER_FILE ==="
echo "=== Running Gradle with preset: $MODEL_PRESET ==="

# Build gradle arguments
GRADLE_ARGS=("-PmodelPreset=$MODEL_PRESET")
GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.modelFile=$MODEL_FILE")
GRADLE_ARGS+=("-Pandroid.testInstrumentationRunnerArguments.tokenizerFile=$TOKENIZER_FILE")

if [ "$MODEL_PRESET" = "custom" ]; then
  GRADLE_ARGS+=("-PcustomPteUrl=$CUSTOM_PTE_URL")
  GRADLE_ARGS+=("-PcustomTokenizerUrl=$CUSTOM_TOKENIZER_URL")
fi

# Run gradle
./gradlew connectedCheck "${GRADLE_ARGS[@]}"
