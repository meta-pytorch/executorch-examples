#!/usr/bin/env python3
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

"""Upload voxtral_rt_exports_wsl model files to HuggingFace Hub."""

from huggingface_hub import HfApi
import os

REPO_ID = "younghan-meta/Voxtral-Mini-4B-Realtime-2602-ExecuTorch-CUDA"
MODEL_DIR = r"C:\Users\younghan\project\executorch\voxtral_rt_exports_wsl"

FILES = [
    "model.pte",
    "preprocessor.pte",
    "aoti_cuda_blob.ptd",
]

def main():
    api = HfApi()

    for filename in FILES:
        filepath = os.path.join(MODEL_DIR, filename)
        if not os.path.exists(filepath):
            print(f"WARNING: {filepath} not found, skipping")
            continue

        size_gb = os.path.getsize(filepath) / (1024 ** 3)
        print(f"Uploading {filename} ({size_gb:.2f} GB)...")

        api.upload_file(
            path_or_fileobj=filepath,
            path_in_repo=filename,
            repo_id=REPO_ID,
            repo_type="model",
        )
        print(f"  Done: {filename}")

    print(f"\nAll files uploaded to https://huggingface.co/{REPO_ID}")

if __name__ == "__main__":
    main()
