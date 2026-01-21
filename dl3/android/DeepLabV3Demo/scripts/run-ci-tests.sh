#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

# CI test script for running Android instrumentation tests
# The model will be downloaded automatically by the test if needed

set -ex

DEMO_NAME=$(basename "$PWD")

echo "=== Test Configuration ==="
echo "Demo: $DEMO_NAME"
echo "Model will be downloaded by the test if not present"

echo "=== Emulator Memory Info ==="
adb shell cat /proc/meminfo | head -5

echo "=== Emulator Disk Space ==="
adb shell df -h /data

# Start logcat capture
LOGCAT_FILE="/tmp/logcat-${DEMO_NAME}.txt"
adb logcat -c
adb logcat > "$LOGCAT_FILE" &
LOGCAT_PID=$!

echo "=== Starting Gradle ==="
./gradlew connectedCheck
TEST_EXIT_CODE=$?

# Stop logcat
if [ -n "$LOGCAT_PID" ]; then
  kill $LOGCAT_PID 2>/dev/null || true
fi

echo "=== Test completion status ==="
if [ $TEST_EXIT_CODE -eq 0 ]; then
  echo "✅ Tests passed successfully"
else
  echo "❌ Tests failed with exit code $TEST_EXIT_CODE"
fi

echo "=== Checking for test results in logcat ==="
grep "SanityCheck" "$LOGCAT_FILE" || echo "No SanityCheck logs found"

exit $TEST_EXIT_CODE
