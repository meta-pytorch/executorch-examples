#!/usr/bin/env python
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

import torch
import torchvision.models as models
from executorch.backends.apple.coreml.partition import CoreMLPartitioner
from executorch.exir import to_edge 
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
from executorch.exir import EdgeCompileConfig, to_edge_transform_and_lower


def main() -> None:
    model = models.mobilenet_v3_small(weights="DEFAULT").eval()
    sample_inputs = (torch.randn(1, 3, 224, 224),)

    et_program_portable = to_edge(
        torch.export.export(model, sample_inputs),
    ).to_executorch() 

    et_program_coreml = to_edge_transform_and_lower(
        torch.export.export(model, sample_inputs),
        partitioner=[CoreMLPartitioner()],
        compile_config=EdgeCompileConfig(_skip_dim_order=True),
    ).to_executorch()

    et_program_xnnpack = to_edge_transform_and_lower(
        torch.export.export(model, sample_inputs),
        partitioner=[XnnpackPartitioner()],
    ).to_executorch()

    with open("mv3.pte", "wb") as file:
        et_program_portable.write_to_file(file)
    with open("mv3_coreml_all.pte", "wb") as file:
        et_program_coreml.write_to_file(file)
    with open("mv3_xnnpack_fp32.pte", "wb") as file:
        et_program_xnnpack.write_to_file(file)


if __name__ == "__main__":
    main()
