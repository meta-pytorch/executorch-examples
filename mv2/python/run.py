# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

import torch
from executorch.runtime import Runtime
from typing import List

runtime = Runtime.get()

input_tensor: torch.Tensor = torch.randn(1, 3, 224, 224)
program = runtime.load_program("model_mv2_xnnpack.pte")
method = program.load_method("forward")
output: List[torch.Tensor] = method.execute([input_tensor])
print("Run succesfully via executorch")

from torchvision.models.mobilenetv2 import MobileNet_V2_Weights
import torchvision.models as models

eager_reference_model = models.mobilenetv2.mobilenet_v2(weights=MobileNet_V2_Weights.DEFAULT).eval()
eager_reference_output = eager_reference_model(input_tensor)

print("Comparing against original PyTorch module")
print(torch.allclose(output[0], eager_reference_output, rtol=1e-3, atol=1e-5))
