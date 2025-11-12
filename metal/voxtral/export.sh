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
            --model "mistralai/Voxtral-Mini-3B-2507" \
            --task "multimodal-text-to-text" \
            --recipe "metal" \
            --dtype bfloat16 \
            --max_seq_len 1024 \
            --output_dir "$ARTIFACT_DIR"

python -m executorch.extension.audio.mel_spectrogram \
            --feature_size 128 \
            --stack_output \
            --max_audio_len 300 \
            --output_file "$ARTIFACT_DIR"/voxtral_preprocessor.pte

curl -L https://huggingface.co/mistralai/Voxtral-Mini-3B-2507/resolve/main/tekken.json -o "$ARTIFACT_DIR"/tekken.json
