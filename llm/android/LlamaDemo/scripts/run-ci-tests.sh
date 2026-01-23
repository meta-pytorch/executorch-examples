#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

# CI test script for running instrumentation tests with pre-downloaded models
# Usage: ./run-ci-tests.sh <model_preset> <model_file> <tokenizer_file> [use_local_aar]
#
# This script is designed for CI environments where models are pre-downloaded
# to /tmp/llama_models/ before the emulator starts.

set -ex

MODEL_PRESET="$1"
MODEL_FILE="$2"
TOKENIZER_FILE="$3"
USE_LOCAL_AAR="${4:-false}"

echo "=== Test Configuration ==="
echo "MODEL_PRESET: $MODEL_PRESET"
echo "MODEL_FILE: $MODEL_FILE"
echo "TOKENIZER_FILE: $TOKENIZER_FILE"
echo "USE_LOCAL_AAR: $USE_LOCAL_AAR"

echo "=== Emulator Memory Info ==="
adb shell cat /proc/meminfo | head -5

echo "=== Emulator Disk Space ==="
adb shell df -h /data

# Clean and prepare device directory
adb shell rm -rf /data/local/tmp/llama
adb shell mkdir -p /data/local/tmp/llama

# Push pre-downloaded model files to device with timeout and retry
echo "=== Pushing pre-downloaded model files to device ==="
for file in /tmp/llama_models/*; do
  filename=$(basename "$file")
  filesize=$(stat -c%s "$file" 2>/dev/null || stat -f%z "$file" 2>/dev/null || echo "0")
  # Calculate timeout: 30 seconds base + 1 second per 50MB
  timeout_secs=$((30 + filesize / 50000000))
  echo "Pushing $filename (size: $((filesize / 1024 / 1024))MB, timeout: ${timeout_secs}s)..."

  max_retries=3
  retry=0
  success=false

  while [ $retry -lt $max_retries ] && [ "$success" = "false" ]; do
    # Run push (ignore exit code, verify by checking file on device)
    timeout $timeout_secs adb push "$file" /data/local/tmp/llama/ || true

    # Verify file was pushed by checking it exists and has correct size
    device_size=$(adb shell "stat -c%s /data/local/tmp/llama/$filename 2>/dev/null || echo 0" | tr -d '\r')
    if [ "$device_size" = "$filesize" ]; then
      success=true
      echo "Successfully pushed $filename (verified size: $device_size bytes)"
    else
      retry=$((retry + 1))
      echo "Push failed or incomplete (attempt $retry/$max_retries, expected $filesize bytes, got $device_size bytes)"
      if [ $retry -lt $max_retries ]; then
        echo "Waiting 5 seconds before retry..."
        sleep 5
        echo "Checking if emulator is still responsive..."
        adb shell getprop ro.build.version.sdk || echo "WARNING: Emulator may be unresponsive"
      fi
    fi
  done

  if [ "$success" = "false" ]; then
    echo "ERROR: Failed to push $filename after $max_retries attempts"
    exit 1
  fi

  echo "Checking emulator responsiveness..."
  adb shell getprop ro.build.version.sdk || echo "WARNING: Emulator may be unresponsive"
done

echo "=== Syncing filesystem ==="
adb shell sync

echo "=== Model directory contents ==="
adb shell ls -la /data/local/tmp/llama/

echo "=== Verifying emulator is responsive ==="
adb shell getprop ro.build.version.sdk

# Start logcat capture
adb logcat -c
adb logcat > /tmp/logcat.txt &
LOGCAT_PID=$!

echo "=== Starting Gradle ==="
GRADLE_ARGS="-PskipModelDownload=true -PmodelPreset=\"$MODEL_PRESET\""
if [ "$USE_LOCAL_AAR" = "true" ]; then
  GRADLE_ARGS="$GRADLE_ARGS -PuseLocalAar=true"
fi
eval ./gradlew connectedCheck "$GRADLE_ARGS"
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
