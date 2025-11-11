#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

ARTIFACT_DIR="$1"

if [ -z "$ARTIFACT_DIR" ]; then
    echo "Error: ARTIFACT_DIR argument not provided."
    echo "Usage: $0 <ARTIFACT_DIR>"
    exit 1
fi

mkdir -p "$ARTIFACT_DIR"

optimum-cli export executorch \
            --model "openai/whisper-large-v3-turbo" \
            --task "automatic-speech-recognition" \
            --recipe "metal" \
            --dtype bfloat16 \
            --output_dir "$ARTIFACT_DIR"

python -m executorch.extension.audio.mel_spectrogram \
            --feature_size 128 \
            --stack_output \
            --max_audio_len 300 \
            --output_file "$ARTIFACT_DIR"/whisper_preprocessor.pte

TOKENIZER_URL="https://huggingface.co/openai/whisper-large-v3-turbo/resolve/main"

curl -L $TOKENIZER_URL/tokenizer.json -o $ARTIFACT_DIR/tokenizer.json
curl -L $TOKENIZER_URL/tokenizer_config.json -o $ARTIFACT_DIR/tokenizer_config.json
curl -L $TOKENIZER_URL/special_tokens_map.json -o $ARTIFACT_DIR/special_tokens_map.json
