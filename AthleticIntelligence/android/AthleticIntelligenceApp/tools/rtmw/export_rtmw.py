#!/usr/bin/env python3
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

"""Export the pinned FP32 RTMW ONNX model to an XNNPACK ExecuTorch program."""

from __future__ import annotations

import argparse
from pathlib import Path

from rtmw_export import export_fp32


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--onnx", required=True, type=Path, help="Verified end2end.onnx path")
    parser.add_argument("--output", required=True, type=Path, help="Output .pte path")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    digest = export_fp32(args.onnx, args.output)
    print(f"wrote {args.output} (SHA-256 {digest})")


if __name__ == "__main__":
    main()
