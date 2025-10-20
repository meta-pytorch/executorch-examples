cmake_minimum_required(VERSION 3.29 FATAL_ERROR)
project(executorch_mv2_demo CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set options for executorch build (based on linux/llm preset)
option(EXECUTORCH_BUILD_EXTENSION_DATA_LOADER "" ON)
option(EXECUTORCH_BUILD_EXTENSION_FLAT_TENSOR "" ON)
option(EXECUTORCH_BUILD_EXTENSION_MODULE "" ON)
option(EXECUTORCH_BUILD_EXTENSION_NAMED_DATA_MAP "" ON)
option(EXECUTORCH_BUILD_EXTENSION_TENSOR "" ON)
option(EXECUTORCH_BUILD_KERNELS_OPTIMIZED "" ON)
option(EXECUTORCH_BUILD_XNNPACK "" ON)
option(EXECUTORCH_XNNPACK_ENABLE_KLEIDI "" OFF)

# Add ExecuTorch subdirectory
add_subdirectory("executorch")

set(DEMO_SOURCES main.cpp)

# Create executable
add_executable(executorch_mv2_demo_app ${DEMO_SOURCES})

# Include directories
target_include_directories(executorch_mv2_demo_app PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

# Link libraries
target_link_libraries(
  executorch_mv2_demo_app
  PRIVATE executorch
          extension_module_static
          extension_tensor
          optimized_native_cpu_ops_lib
          xnnpack_backend
)

# Set output directory
set_target_properties(executorch_mv2_demo_app
    PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
)
