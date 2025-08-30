# Copyright (c) Meta Platforms, Inc. and affiliates. All rights reserved.
#
# This source code is licensed under the BSD-style license found in the LICENSE
# file in the root directory of this source tree.

# Please this file formatted by running:
# ~~~
# cmake-format -i CMakeLists.txt
# ~~~

add_subdirectory("executorch")

# Tokenizer
add_executable(tokenizer_js)

target_compile_options(tokenizer_js PRIVATE ${_common_compile_options})
target_include_directories(tokenizer_js PRIVATE ${_common_include_directories})

target_include_directories(tokenizer_js PRIVATE ${_common_include_directories})

target_link_libraries(tokenizer_js PRIVATE tokenizers_wasm tokenizers::regex_lookahead)
target_link_options(
  tokenizer_js PRIVATE -sALLOW_MEMORY_GROWTH
  -sMODULARIZE=1 -sEXPORT_NAME=loadTokenizers -sENVIRONMENT=web)

set_target_properties(
  tokenizer_js PROPERTIES OUTPUT_NAME "tokenizers"
)


# Bindings
add_executable(executorch_wasm_demo_lib)
target_link_libraries(executorch_wasm_demo_lib
                      PRIVATE executorch_wasm executorch_backends executorch)
target_link_options(executorch_wasm_demo_lib PRIVATE -sALLOW_MEMORY_GROWTH
                    -sSTACK_SIZE=262144 -sENVIRONMENT=web)

add_custom_command(
  OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/demo.js
  COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_SOURCE_DIR}/demo.js
          ${CMAKE_CURRENT_BINARY_DIR}/demo.js
  DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/demo.js
  COMMENT "Copying demo.js to build output directory")

add_custom_command(
  OUTPUT ${CMAKE_CURRENT_BINARY_DIR}/demo.html
  COMMAND ${CMAKE_COMMAND} -E copy ${CMAKE_CURRENT_SOURCE_DIR}/demo.html
          ${CMAKE_CURRENT_BINARY_DIR}/demo.html
  DEPENDS ${CMAKE_CURRENT_SOURCE_DIR}/demo.html
  COMMENT "Copying demo.html to build output directory")

add_custom_target(
  executorch_wasm_demo
  DEPENDS executorch_wasm_demo_lib ${CMAKE_CURRENT_BINARY_DIR}/demo.js
          ${CMAKE_CURRENT_BINARY_DIR}/demo.html)

add_dependencies(executorch_wasm_demo tokenizer_js)
