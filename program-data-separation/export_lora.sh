#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

set -exu

# Parse command line arguments.
QUANTIZE=false
while getopts "q" opt; do
    case ${opt} in
        q)
            QUANTIZE=true
            ;;
        *)
            echo "Usage: $0 [-q]"
            echo "  -q  Enable quantization (8da4w, group_size=32)"
            exit 1
            ;;
    esac
done

# Install huggingface_hub for downloading model artifacts.
python -m pip install -q huggingface_hub

# Download LoRA adapter and config.
HF_ADAPTER_REPO="lucylq/qwen3_06B_lora_math"
HF_ADAPTER_PATH=$(python -c "from huggingface_hub import snapshot_download; print(snapshot_download('${HF_ADAPTER_REPO}'))")
echo "LoRA adapter downloaded to: $HF_ADAPTER_PATH"

# Download Qwen3-0.6B model.
HF_QWEN_PATH=$(python -c "from huggingface_hub import snapshot_download; print(snapshot_download('unsloth/Qwen3-0.6B'))")
echo "Model downloaded to: $HF_QWEN_PATH"

# Output directory.
DIR="models"
mkdir -p "${DIR}"

# Set model names and quantization args based on -q flag.
SCRIPT_DIR="$(dirname "${BASH_SOURCE[0]}")"
CONFIG="${SCRIPT_DIR}/config/qwen3_xnnpack.yaml"
if [ "$QUANTIZE" = true ]; then
    MODEL="qwen3_06B_q"
    LORA_MODEL="qwen3_06B_lora_q"
    FOUNDATION_WEIGHTS="qwen3_06B_q"
    QUANT_ARGS=("+quantization.qmode=8da4w" "+quantization.group_size=32" "+quantization.embedding_quantize=\"8,0\"")
else
    MODEL="qwen3_06B"
    LORA_MODEL="qwen3_06B_lora"
    FOUNDATION_WEIGHTS="qwen3_06B"
    QUANT_ARGS=()
fi

# Export a non-LoRA Qwen model with program-data separated.
python -m executorch.extension.llm.export.export_llm \
    --config "${CONFIG}" \
    +export.output_name="${DIR}/${MODEL}.pte" \
    +export.foundation_weights_file="${DIR}/${FOUNDATION_WEIGHTS}.ptd" \
    "${QUANT_ARGS[@]}"

# Export a LoRA Qwen model with program-data separated.
python -m executorch.extension.llm.export.export_llm \
    --config "${CONFIG}" \
    +base.adapter_checkpoint="${HF_ADAPTER_PATH}/adapter_model.safetensors" \
    +base.adapter_config="${HF_ADAPTER_PATH}/adapter_config.json" \
    +export.output_name="${DIR}/${LORA_MODEL}.pte" \
    +export.foundation_weights_file="${DIR}/${FOUNDATION_WEIGHTS}.ptd" \
    +export.lora_weights_file="${DIR}/${LORA_MODEL}.ptd" \
    "${QUANT_ARGS[@]}"
