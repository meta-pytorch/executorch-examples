/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

#include <executorch/extension/module/module.h>
#include <executorch/extension/tensor/tensor.h>
#include <iostream>

using namespace ::executorch::extension;

int main(int argc, char* argv[]) {
    // Load the model.
    Module module("../python/model_mv2_xnnpack.pte");

    // Create an input tensor.
    float input[1 * 3 * 224 * 224];
    auto tensor = from_blob(input, {1, 3, 224, 224});

    // Perform an inference.
    const auto result = module.forward(tensor);

    if (result.ok()) {
        // Retrieve the output data.
        const auto output = result->at(0).toTensor().const_data_ptr<float>();
        std::cout << "Success" << std::endl;
    }
}
