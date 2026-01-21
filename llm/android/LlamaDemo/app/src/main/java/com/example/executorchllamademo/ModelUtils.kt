/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

/**
 * Utility object for model categorization based on model type and backend.
 */
object ModelUtils {
    // XNNPACK or QNN or Vulkan
    const val TEXT_MODEL = 1

    // XNNPACK or Vulkan
    const val VISION_MODEL = 2
    const val VISION_MODEL_IMAGE_CHANNELS = 3
    const val VISION_MODEL_SEQ_LEN = 2048
    const val TEXT_MODEL_SEQ_LEN = 768

    // MediaTek
    const val MEDIATEK_TEXT_MODEL = 3

    // QNN static llama
    const val QNN_TEXT_MODEL = 4

    @JvmStatic
    fun getModelCategory(modelType: ModelType, backendType: BackendType): Int {
        return when {
            backendType == BackendType.XNNPACK || backendType == BackendType.VULKAN -> {
                when (modelType) {
                    ModelType.GEMMA_3, ModelType.LLAVA_1_5, ModelType.VOXTRAL -> VISION_MODEL
                    else -> TEXT_MODEL
                }
            }
            backendType == BackendType.MEDIATEK -> MEDIATEK_TEXT_MODEL
            backendType == BackendType.QUALCOMM -> QNN_TEXT_MODEL
            else -> TEXT_MODEL // default
        }
    }
}
