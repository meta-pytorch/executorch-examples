# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

# pyre-unsafe

import argparse
import os

from functools import partial
from typing import Dict, final, Optional, Sequence, Type

import executorch.exir as exir
import torch

from executorch.exir import (
    EdgeCompileConfig,
    ExecutorchBackendConfig,
    to_edge,
    to_edge_transform_and_lower,
)
from executorch.exir.passes.external_constants_pass import (
    delegate_external_constants_pass_unlifted,
)
from executorch.exir.program import ExecutorchProgramManager
from torch.export import export


class ModuleLinear(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.linear = torch.nn.Linear(3, 3)

    def forward(self, x: torch.Tensor):
        return self.linear(x)

    def get_random_inputs(self):
        return (torch.randn(3),)


def main() -> None:

    parser = argparse.ArgumentParser(
        prog="export_program",
        description="Exports nn.Module models to ExecuTorch .pte and .ptd files",
    )
    parser.add_argument(
        "--outdir",
        type=str,
        required=True,
        help="Path to the directory to write <classname>.pte files and .ptd files to",
    )
    parser.add_argument(
        "--xnnpack",
        action="store_true",
        help="Export the model lowered to XNNPACK",
    )
    args = parser.parse_args()

    if args.xnnpack:
        print("Exporting to ExecuTorch with XNNPACK")
    else:
        print("Exporting to ExecuTorch")

    # Construct eager model.
    model = ModuleLinear()
    # Export model.
    exported_program = torch.export.export(model, model.get_random_inputs())
    model_name = "linear_xnnpack" if args.xnnpack else "linear"

    # Lower to XNNPACK.
    if args.xnnpack:
        print("Lowering to XNNPACK...")
        from executorch.backends.xnnpack.partition.xnnpack_partitioner import (
            XnnpackPartitioner,
        )

        partial_function = partial(
            delegate_external_constants_pass_unlifted,
            ep=exported_program,
            gen_tag_fn=lambda x: model_name,
        )
        executorch_program = to_edge_transform_and_lower(
            exported_program,
            transform_passes=[partial_function],
            compile_config=EdgeCompileConfig(_check_ir_validity=False),
            partitioner=[XnnpackPartitioner()],
        ).to_executorch(config=ExecutorchBackendConfig())

    # No backends.
    else:
        print("Lowering to ExecuTorch...")
        edge_program = to_edge(exported_program)
        executorch_program = edge_program.to_executorch(
            ExecutorchBackendConfig(external_constants=True)
        )

    print("Saving PTE and PTD files.")
    os.makedirs(args.outdir, exist_ok=True)
    pte_file = os.path.join(args.outdir, f"{model_name}.pte")
    with open(pte_file, "wb") as fp:
        executorch_program.write_to_file(fp)
    if executorch_program._tensor_data.get("_default_external_constant"):
        executorch_program._tensor_data[model_name] = (
            executorch_program._tensor_data.pop("_default_external_constant")
        )
    executorch_program.write_tensor_data_to_file(args.outdir)

    print(f"Successfully exported {model_name}.pte and {model_name}.ptd")


if __name__ == "__main__":
    main()
