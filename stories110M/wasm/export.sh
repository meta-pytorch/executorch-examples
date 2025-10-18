#!/usr/bin/env bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

temp_pt="$(mktemp).pt"
temp_params="$(mktemp).json"

wget -O "$temp_pt" "https://huggingface.co/karpathy/tinyllamas/resolve/main/stories110M.pt"
wget "https://raw.githubusercontent.com/karpathy/llama2.c/master/tokenizer.model"
echo '{"dim": 768, "multiple_of": 32, "n_heads": 12, "n_layers": 12, "norm_eps": 1e-05, "vocab_size": 32000}' > "$temp_params"

cd executorch

python -m extension.llm.export.export_llm base.checkpoint="$temp_pt" base.params="$temp_params" backend.xnnpack.enabled=True model.use_kv_cache=True export.output_name=../stories110M.pte

rm "$temp_pt" "$temp_params"
