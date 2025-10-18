#!/usr/bin/env bash
# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

cd executorch

python -c "
from examples.models.model_factory import EagerModelFactory
from executorch.backends.xnnpack.partition.xnnpack_partitioner import XnnpackPartitioner
from executorch.exir import to_edge_transform_and_lower
from torch.export import export

model, example_inputs, _, _ = EagerModelFactory.create_model(
    'efficient_sam', 'EfficientSAM'
)

prog = export(model, example_inputs)
edge = to_edge_transform_and_lower(prog, partitioner=[XnnpackPartitioner()])
exec_prog = edge.to_executorch()
with open('../xnnpack_efficient_sam.pte', 'wb') as file:
    exec_prog.write_to_file(file)
"
