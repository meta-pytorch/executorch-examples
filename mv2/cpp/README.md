# ExecuTorch MobileNetV2 Demo C++ Application

This is a simple C++ demo application that uses the ExecuTorch library for MobileNetV2 model inference.

## Build instructions

0. Export the model. See [mv2/python/README.md](../python/README.md)

1. The ExecuTorch repository is configured as a git submodule at `~/executorch-examples/third-party/executorch/`, with `mv2/cpp/executorch` being a symlink to it. To initialize:
   ```bash
    cd ~/executorch-examples/
    git submodule sync
    git submodule update --init --recursive
   ```

2. Install dev requirements for ExecuTorch

    ```bash
    cd ~/executorch-examples/mv2/cpp/executorch
    pip install -r requirements-dev.txt
    ```

3. Build the project:
   ```bash
   cd ~/executorch-examples/mv2/cpp
   chmod +x build.sh
   ./build.sh
   ```

4. Run the demo application:
   ```bash
   ./build/bin/executorch_mv2_demo_app
   ```

## Dependencies

- CMake 3.18 or higher
- C++17 compatible compiler
- ExecuTorch library (release/1.0)

## Notes

- Make sure you have the correct model file (`.pte`) compatible with ExecuTorch.
- This demo currently initializes the input tensor with random data. In a real application, you would replace this with actual input data.
