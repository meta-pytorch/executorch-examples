#!/bin/bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

set -exu

python -m pip install torchtune==0.7.0.dev20250730  --extra-index-url https://download.pytorch.org/whl/nightly/cpu

# Download model artifacts from HF.
DOWNLOADED_PATH=$(python -c "
from huggingface_hub import snapshot_download
path=snapshot_download(
    repo_id=\"lucylq/llama3_1B_lora\",
)
import os
print(path)
")

# Copy over tokenizer, for use at runtime.
cp "${DOWNLOADED_PATH}/tokenizer.model" .

# Export a non-LoRA model with program-data separated.
MODEL="llama_3_2_1B"
python -m executorch.extension.llm.export.export_llm \
    base.checkpoint="${DOWNLOADED_PATH}/consolidated.00.pth" \
    base.params="${DOWNLOADED_PATH}/params.json" \
    base.tokenizer_path="${DOWNLOADED_PATH}/tokenizer.model" \
    model.use_kv_cache=true \
    model.use_sdpa_with_kv_cache=true \
    model.dtype_override="fp32" \
    backend.xnnpack.enabled=true \
    backend.xnnpack.extended_ops=true \
    export.output_name="${MODEL}.pte" \
    export.foundation_weights_file="${MODEL}.ptd"

# Export a LoRA model, with program and data separated.
LORA_MODEL="llama_3_2_1B_lora"
python -m executorch.extension.llm.export.export_llm \
    base.checkpoint="${DOWNLOADED_PATH}/consolidated.00.pth" \
    base.params="${DOWNLOADED_PATH}/params.json" \
    base.adapter_checkpoint="${DOWNLOADED_PATH}/adapter_model.pt" \
    base.adapter_config="${DOWNLOADED_PATH}/adapter_config.json" \
    base.tokenizer_path="${DOWNLOADED_PATH}/tokenizer.model" \
    model.use_kv_cache=true \
    model.use_sdpa_with_kv_cache=true \
    model.dtype_override="fp32" \
    backend.xnnpack.enabled=true \
    backend.xnnpack.extended_ops=true \
    export.output_name="${LORA_MODEL}.pte" \
    export.foundation_weights_file="foundation.ptd"
