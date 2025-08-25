#!/bin/bash
set -e

# Clean and create build directory if it doesn't exist
rm -rf build
mkdir -p build
cd build

# Configure CMake
cmake -DCMAKE_BUILD_TYPE=Release -DEXECUTORCH_BUILD_LORA_DEMO=True -DEXECUTORCH_XNNPACK_ENABLE_WEIGHT_CACHE=True ../..

# Build the project
cmake --build . -j$(nproc)

echo "Build complete! Executable located at: ./build/bin/executorch_program_data_separation"
