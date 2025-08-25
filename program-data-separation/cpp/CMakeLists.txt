
cmake_minimum_required(VERSION 3.29 FATAL_ERROR)
project(executorch_ptd CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set options for executorch build.
option(EXECUTORCH_ENABLE_LOGGING "" ON)
option(EXECUTORCH_BUILD_EXTENSION_DATA_LOADER "" ON)
option(EXECUTORCH_BUILD_EXTENSION_FLAT_TENSOR "" ON)
option(EXECUTORCH_BUILD_EXTENSION_MODULE "" ON)
option(EXECUTORCH_BUILD_EXTENSION_TENSOR "" ON)
option(EXECUTORCH_BUILD_KERNELS_OPTIMIZED "" ON)
option(EXECUTORCH_BUILD_XNNPACK "" ON)

# Dependencies required for llm runner in lora demo.
if(EXECUTORCH_BUILD_LORA_DEMO)
option(EXECUTORCH_BUILD_EXTENSION_LLM "" ON)
option(EXECUTORCH_BUILD_EXTENSION_LLM_RUNNER "" ON)
option(EXECUTORCH_BUILD_KERNELS_LLM "" ON)
option(EXECUTORCH_BUILD_KERNELS_LLM_AOT "" ON)
endif()

# Add ExecuTorch subdirectory, after setting options.
add_subdirectory("executorch")

set(LINK_LIBS executorch
              executorch::extensions
              xnnpack_backend
              # NOTE: xnnpack_backend has to go before
              # kernels otherwise it doesn't get registered.
              executorch::kernels
              gflags
)

# Add sources and dependencies.
set(DEMO_SOURCES "")
if(EXECUTORCH_BUILD_LINEAR_DEMO)
  list(APPEND DEMO_SOURCES "linear_example/main.cpp")
endif()
if(EXECUTORCH_BUILD_LORA_DEMO)
  list(APPEND DEMO_SOURCES "lora_example/main.cpp")
endif()

# Create executable
add_executable(executorch_program_data_separation ${DEMO_SOURCES})

# Link libraries
target_link_libraries(
  executorch_program_data_separation
  PRIVATE ${LINK_LIBS}
)

# Include directories for lora demo.
if(EXECUTORCH_BUILD_LORA_DEMO)
  # Include directories
  target_include_directories(executorch_program_data_separation PRIVATE
      ${CMAKE_CURRENT_SOURCE_DIR}
      ${CMAKE_CURRENT_SOURCE_DIR}/executorch/extension/llm/tokenizers/include
  )
  target_link_libraries(
    executorch_program_data_separation
    PUBLIC tokenizers::tokenizers
  )
endif()

# Set output directory
set_target_properties(executorch_program_data_separation
    PROPERTIES
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
)
