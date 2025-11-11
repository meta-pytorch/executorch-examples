#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

if [ -z "$1" ] || [ -z "$2" ]; then
  echo "Error: Input audio path and artifact directory path must be provided as arguments"
  echo "Usage: $0 <input_audio_path> <artifact_dir>"
  echo ""
  echo "Arguments:"
  echo "  <input_audio_path>  Path to the input audio file"
  echo "  <artifact_dir>      Path to directory containing model files"
  exit 1
fi

INPUT_AUDIO_PATH="$1"
ARTIFACT_DIR="$2"

# Check if input audio file exists
if [ ! -f "$INPUT_AUDIO_PATH" ]; then
  echo "Error: Input audio file not found: $INPUT_AUDIO_PATH"
  exit 1
fi

# Check if directory exists
if [ ! -d "$ARTIFACT_DIR" ]; then
  echo "Error: Directory not found: $ARTIFACT_DIR"
  exit 1
fi

# Check for required files
REQUIRED_FILES=("model.pte" "aoti_metal_blob.ptd" "whisper_preprocessor.pte")
for file in "${REQUIRED_FILES[@]}"; do
  if [ ! -f "$ARTIFACT_DIR/$file" ]; then
    echo "Error: Required file not found: $ARTIFACT_DIR/$file"
    exit 1
  fi
done

/usr/bin/time -l ./cmake-out/examples/models/whisper/whisper_runner \
      --model_path "$ARTIFACT_DIR"/model.pte \
      --data_path "$ARTIFACT_DIR"/aoti_metal_blob.ptd \
      --tokenizer_path "$ARTIFACT_DIR"/ \
      --audio_path "$INPUT_AUDIO_PATH" \
      --processor_path "$ARTIFACT_DIR"/whisper_preprocessor.pte \
      --model_name "large-v3-turbo" \
      --temperature 0
