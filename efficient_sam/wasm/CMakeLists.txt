# Copyright (c) Meta Platforms, Inc. and affiliates. All rights reserved.
#
# This source code is licensed under the BSD-style license found in the LICENSE
# file in the root directory of this source tree.

# Please this file formatted by running:
# ~~~
# cmake-format -i CMakeLists.txt
# ~~~

cmake_minimum_required(VERSION 3.19)
project(efficient_sam_wasm_demo)

add_subdirectory("executorch")

add_executable(executorch_wasm_demo_lib)
target_link_libraries(executorch_wasm_demo_lib PRIVATE executorch_wasm
                                                       executorch_backends)
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
