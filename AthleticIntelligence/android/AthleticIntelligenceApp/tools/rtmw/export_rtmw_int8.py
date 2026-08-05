#!/usr/bin/env python3
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

"""Calibrate and export the pinned RTMW model as an int8 XNNPACK program."""

from __future__ import annotations

import argparse
import hashlib
import tempfile
from pathlib import Path

from rtmw_export import (
    INPUT_HEIGHT,
    INPUT_WIDTH,
    lower_to_xnnpack,
    prepare_rtmw_model,
    sha256_file,
)


IMAGE_SUFFIXES = {".jpeg", ".jpg", ".png"}


def calibration_files(inputs: list[Path]) -> list[Path]:
    files: list[Path] = []
    for value in inputs:
        if value.is_dir():
            files.extend(path for path in value.rglob("*") if path.is_file())
        elif value.is_file():
            files.append(value)
        else:
            raise FileNotFoundError(f"Calibration input not found: {value}")
    selected = sorted(
        {path.resolve() for path in files if path.suffix.lower() in IMAGE_SUFFIXES},
        key=lambda path: str(path),
    )
    if not selected:
        raise ValueError("At least one .jpg, .jpeg, or .png calibration image is required")
    return selected


def calibration_digest(paths: list[Path]) -> str:
    digest = hashlib.sha256()
    for path in paths:
        digest.update(bytes.fromhex(sha256_file(path)))
    return digest.hexdigest()


def load_calibration_image(path: Path):
    import numpy as np
    import torch
    from PIL import Image

    mean = np.array([123.675, 116.28, 103.53], dtype=np.float32)
    standard_deviation = np.array([58.395, 57.12, 57.375], dtype=np.float32)
    with Image.open(path) as source:
        image = source.convert("RGB")
        scale = min(INPUT_WIDTH / image.width, INPUT_HEIGHT / image.height)
        resized_width = max(1, round(image.width * scale))
        resized_height = max(1, round(image.height * scale))
        image = image.resize(
            (resized_width, resized_height),
            resample=Image.Resampling.BILINEAR,
        )
        pixels = np.zeros((INPUT_HEIGHT, INPUT_WIDTH, 3), dtype=np.float32)
        left = (INPUT_WIDTH - resized_width) // 2
        top = (INPUT_HEIGHT - resized_height) // 2
        pixels[top : top + resized_height, left : left + resized_width] = np.asarray(
            image, dtype=np.float32
        )
    normalized = (pixels - mean) / standard_deviation
    return torch.from_numpy(normalized.transpose(2, 0, 1)[None]).contiguous()


def export_int8(onnx_path: Path, output_path: Path, calibration: list[Path]) -> str:
    import torch
    from executorch.backends.xnnpack.quantizer.xnnpack_quantizer import (
        XNNPACKQuantizer,
        get_symmetric_quantization_config,
    )
    from torchao.quantization.pt2e.quantize_pt2e import convert_pt2e, prepare_pt2e

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="rtmw-int8-export-") as temporary:
        model, example = prepare_rtmw_model(onnx_path, Path(temporary))
        exported_module = torch.export.export(model, (example,)).module()
        quantizer = XNNPACKQuantizer().set_global(
            get_symmetric_quantization_config(is_per_channel=True)
        )
        prepared = prepare_pt2e(exported_module, quantizer)
        with torch.no_grad():
            for image_path in calibration:
                prepared(load_calibration_image(image_path))
        quantized = convert_pt2e(prepared)
        with torch.no_grad():
            exported_program = torch.export.export(quantized, (example,))
        output_path.write_bytes(lower_to_xnnpack(exported_program))
    return sha256_file(output_path)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--onnx", required=True, type=Path, help="Verified end2end.onnx path")
    parser.add_argument("--output", required=True, type=Path, help="Output int8 .pte path")
    parser.add_argument(
        "--calibration",
        required=True,
        type=Path,
        nargs="+",
        help="Representative person-crop image file(s) or directories",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    calibration = calibration_files(args.calibration)
    print(f"calibration images: {len(calibration)}")
    print(f"calibration content SHA-256: {calibration_digest(calibration)}")
    digest = export_int8(args.onnx, args.output, calibration)
    print(f"wrote {args.output} (SHA-256 {digest})")


if __name__ == "__main__":
    main()
