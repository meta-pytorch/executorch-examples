/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo;

public class ModelUtils {
  // XNNPACK or QNN or Vulkan
  static final int TEXT_MODEL = 1;

  // XNNPACK or Vulkan
  static final int VISION_MODEL = 2;
  static final int VISION_MODEL_IMAGE_CHANNELS = 3;
  static final int VISION_MODEL_SEQ_LEN = 2048;
  static final int TEXT_MODEL_SEQ_LEN = 768;

  // MediaTek
  static final int MEDIATEK_TEXT_MODEL = 3;

  // QNN static llama
  static final int QNN_TEXT_MODEL = 4;

  public static int getModelCategory(ModelType modelType, BackendType backendType) {
    if (backendType.equals(BackendType.XNNPACK) || backendType.equals(BackendType.VULKAN)) {
      switch (modelType) {
        case GEMMA_3:
        case LLAVA_1_5:
        case VOXTRAL:
          return VISION_MODEL;
        case LLAMA_3:
        case QWEN_3:
        default:
          return TEXT_MODEL;
      }
    } else if (backendType.equals(BackendType.MEDIATEK)) {
      return MEDIATEK_TEXT_MODEL;
    } else if (backendType.equals(BackendType.QUALCOMM)) {
      return QNN_TEXT_MODEL;
    }

    return TEXT_MODEL; // default
  }
}
