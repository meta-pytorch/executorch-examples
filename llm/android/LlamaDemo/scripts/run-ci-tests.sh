#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

# CI test script for running instrumentation tests with pre-downloaded models
# Usage: ./run-ci-tests.sh <model_preset> <model_file> <tokenizer_file>
#
# This script is designed for CI environments where models are pre-downloaded
# to /tmp/llama_models/ before the emulator starts.

set -ex

MODEL_PRESET="$1"
MODEL_FILE="$2"
TOKENIZER_FILE="$3"

echo "=== Test Configuration ==="
echo "MODEL_PRESET: $MODEL_PRESET"
echo "MODEL_FILE: $MODEL_FILE"
echo "TOKENIZER_FILE: $TOKENIZER_FILE"

echo "=== Emulator Memory Info ==="
adb shell cat /proc/meminfo | head -5

# Clean and prepare device directory
adb shell rm -rf /data/local/tmp/llama
adb shell mkdir -p /data/local/tmp/llama

# Push pre-downloaded model files to device
echo "=== Pushing pre-downloaded model files to device ==="
for file in /tmp/llama_models/*; do
  echo "Pushing $(basename "$file")..."
  adb push "$file" /data/local/tmp/llama/
done

echo "=== Model directory contents ==="
adb shell ls -la /data/local/tmp/llama/

echo "=== Verifying emulator is responsive ==="
adb shell getprop ro.build.version.sdk

# Start logcat capture
adb logcat -c
adb logcat > /tmp/logcat.txt &
LOGCAT_PID=$!

echo "=== Starting Gradle ==="
./gradlew connectedCheck \
  -PskipModelDownload=true \
  -PmodelPreset="$MODEL_PRESET" \
  -Pandroid.testInstrumentationRunnerArguments.modelFile="$MODEL_FILE" \
  -Pandroid.testInstrumentationRunnerArguments.tokenizerFile="$TOKENIZER_FILE"
TEST_EXIT_CODE=$?

echo "=== Model directory after Gradle ==="
adb shell ls -la /data/local/tmp/llama/

# Stop logcat
kill $LOGCAT_PID || true

echo "=== Model configuration used by test ==="
grep "UIWorkflowTest.*Using model" /tmp/logcat.txt || echo "Model config not found in logcat"

echo "=== Searching for LLAMA_RESPONSE in logcat ==="
grep "LLAMA_RESPONSE" /tmp/logcat.txt || echo "No LLAMA_RESPONSE found in logcat"
grep "LLAMA_RESPONSE" /tmp/logcat.txt | sed 's/.*LLAMA_RESPONSE: //' | grep -v "BEGIN_RESPONSE\|END_RESPONSE" > /tmp/response.txt || true

echo "=== Response file contents ==="
cat /tmp/response.txt || echo "Response file empty or not created"

# Cleanup
adb shell rm -rf /data/local/tmp/llama

exit $TEST_EXIT_CODE
