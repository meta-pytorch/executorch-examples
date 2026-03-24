#!/bin/bash

# Copyright (c) Meta Platforms, Inc. and affiliates.
# All rights reserved.
#
# This source code is licensed under the BSD-style license found in the
# LICENSE file in the root directory of this source tree.

set -e

# Create build directory if it doesn't exist
mkdir -p build
cd build

# Configure CMake
cmake -DCMAKE_BUILD_TYPE=Release ..

# Build the project
cmake --build . -j$(nproc)

echo "Build complete! Executable located at: ./bin/executorch_mv2_demo_app"
