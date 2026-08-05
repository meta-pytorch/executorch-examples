#!/usr/bin/env python3
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

"""Shared, fixed-shape RTMW ONNX-to-ExecuTorch export helpers."""

from __future__ import annotations

import hashlib
import tempfile
from pathlib import Path
from typing import Any


RTMW_ARCHIVE_URL = (
    "https://download.openmmlab.com/mmpose/v1/projects/rtmw/onnx_sdk/"
    "rtmw-dw-x-l_simcc-cocktail14_270e-384x288_20231122.zip"
)
RTMW_ARCHIVE_SHA256 = "a87e1af41a0a067776dba7d46e1c21c8f6e9f18e247e0e606718dd1f31e96ffd"
RTMW_ONNX_SHA256 = "bd033156e5104c4f5d2edfe0453e02661e30a2f3da453ec93c8764d561b83054"
INPUT_HEIGHT = 384
INPUT_WIDTH = 288
KEYPOINT_COUNT = 133


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def require_source_model(path: Path) -> None:
    if not path.is_file():
        raise FileNotFoundError(f"RTMW ONNX file not found: {path}")
    actual = sha256_file(path)
    if actual != RTMW_ONNX_SHA256:
        raise ValueError(
            "Unexpected RTMW ONNX SHA-256. "
            f"Expected {RTMW_ONNX_SHA256}, got {actual}."
        )


def prepare_rtmw_model(onnx_path: Path, work_directory: Path) -> tuple[Any, Any]:
    """Convert the verified ONNX model into a fixed-shape eager PyTorch module."""
    import numpy as np
    import onnx
    import onnxslim
    import torch
    import torch.nn as nn
    from onnx import numpy_helper
    from onnx2torch import convert
    from onnx2torch.node_converters.reshape import OnnxReshape
    from onnx2torch.node_converters.resize import OnnxResize

    require_source_model(onnx_path)
    model_proto = onnx.load(onnx_path)
    maximum_name = "rtmw_clip_max_float32"
    model_proto.graph.initializer.append(
        numpy_helper.from_array(np.array(np.finfo(np.float32).max, dtype=np.float32), maximum_name)
    )
    for node in model_proto.graph.node:
        if node.op_type == "Clip" and len(node.input) == 3 and node.input[2] == "":
            node.input[2] = maximum_name

    patched_path = work_directory / "rtmw_patched.onnx"
    folded_path = work_directory / "rtmw_folded.onnx"
    onnx.save(model_proto, patched_path)
    folded = onnxslim.slim(
        str(patched_path),
        model_check=False,
        input_shapes=[f"input:1,3,{INPUT_HEIGHT},{INPUT_WIDTH}"],
    )
    onnx.save(folded, folded_path)

    class StaticUpsample2x(nn.Module):
        def forward(self, value: Any, *args: Any, **kwargs: Any) -> Any:
            del args, kwargs
            return torch.nn.functional.interpolate(value, scale_factor=2.0, mode="nearest")

    class StaticReshapeSimCC(nn.Module):
        def forward(self, value: Any, *args: Any, **kwargs: Any) -> Any:
            del args, kwargs
            return value.reshape(1, KEYPOINT_COUNT, -1)

    eager_model = convert(str(folded_path)).eval()

    def replace_dynamic_operators(module: nn.Module) -> None:
        for name, child in module.named_children():
            if isinstance(child, OnnxResize):
                setattr(module, name, StaticUpsample2x())
            elif isinstance(child, OnnxReshape):
                setattr(module, name, StaticReshapeSimCC())
            else:
                replace_dynamic_operators(child)

    replace_dynamic_operators(eager_model)
    for name, buffer in list(eager_model.named_buffers()):
        if buffer.numel() != 0:
            continue
        parent_name, _, attribute = name.rpartition(".")
        parent = eager_model.get_submodule(parent_name) if parent_name else eager_model
        parent.register_buffer(attribute, torch.zeros(1, dtype=buffer.dtype))

    torch.manual_seed(0)
    example = torch.randn(1, 3, INPUT_HEIGHT, INPUT_WIDTH)
    with torch.no_grad():
        simcc_x, simcc_y = eager_model(example)
    if simcc_x.shape[1] != KEYPOINT_COUNT or simcc_y.shape[1] != KEYPOINT_COUNT:
        raise RuntimeError(
            f"Unexpected output shapes: {tuple(simcc_x.shape)}, {tuple(simcc_y.shape)}"
        )
    return eager_model, example


def lower_to_xnnpack(exported_program: Any) -> bytes:
    from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
    from executorch.exir import to_edge_transform_and_lower

    edge_program = to_edge_transform_and_lower(
        exported_program,
        partitioner=[XnnpackPartitioner()],
    )
    return bytes(edge_program.to_executorch().buffer)


def export_fp32(onnx_path: Path, output_path: Path) -> str:
    import torch

    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="rtmw-export-") as temporary:
        model, example = prepare_rtmw_model(onnx_path, Path(temporary))
        with torch.no_grad():
            exported_program = torch.export.export(model, (example,))
        output_path.write_bytes(lower_to_xnnpack(exported_program))
    return sha256_file(output_path)
