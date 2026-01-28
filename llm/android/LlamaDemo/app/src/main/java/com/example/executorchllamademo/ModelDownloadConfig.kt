/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

/**
 * Represents a downloadable model with its associated files.
 */
data class ModelInfo(
    val displayName: String,
    val modelUrl: String,
    val modelFilename: String,
    val tokenizerUrl: String,
    val tokenizerFilename: String,
    val modelType: ModelType
) {
    fun hasTokenizer(): Boolean = tokenizerUrl.isNotEmpty()
}

/**
 * Configuration class that maps model display names to their download URLs.
 */
object ModelDownloadConfig {

    private val AVAILABLE_MODELS: LinkedHashMap<String, ModelInfo> = linkedMapOf(
        "stories" to ModelInfo(
            displayName = "Stories 110M",
            modelUrl = "https://ossci-android.s3.amazonaws.com/executorch/stories/snapshot-20260114/stories110M.pte",
            modelFilename = "stories110M.pte",
            tokenizerUrl = "https://ossci-android.s3.amazonaws.com/executorch/stories/snapshot-20260114/tokenizer.model",
            tokenizerFilename = "tokenizer.model",
            modelType = ModelType.LLAMA_3
        ),
        "llama" to ModelInfo(
            displayName = "Llama 3.2 1B",
            modelUrl = "https://huggingface.co/executorch-community/Llama-3.2-1B-ET/resolve/main/llama3_2-1B.pte",
            modelFilename = "llama3_2-1B.pte",
            tokenizerUrl = "https://huggingface.co/executorch-community/Llama-3.2-1B-ET/resolve/main/tokenizer.model",
            tokenizerFilename = "tokenizer.model",
            modelType = ModelType.LLAMA_3
        ),
        "gemma" to ModelInfo(
            displayName = "Gemma 3 4B",
            modelUrl = "https://huggingface.co/pytorch/gemma-3-4b-it-HQQ-INT8-INT4/resolve/main/model.pte",
            modelFilename = "model.pte",
            tokenizerUrl = "https://huggingface.co/pytorch/gemma-3-4b-it-HQQ-INT8-INT4/resolve/main/tokenizer.json",
            tokenizerFilename = "tokenizer.json",
            modelType = ModelType.GEMMA_3
        )
    )

    fun getAvailableModels(): Map<String, ModelInfo> = AVAILABLE_MODELS

    fun getDisplayNames(): Array<String> =
        AVAILABLE_MODELS.values.map { it.displayName }.toTypedArray()

    fun getModelKeys(): Array<String> = AVAILABLE_MODELS.keys.toTypedArray()

    fun getByDisplayName(displayName: String): ModelInfo? =
        AVAILABLE_MODELS.values.find { it.displayName == displayName }

    fun getByKey(key: String): ModelInfo? = AVAILABLE_MODELS[key]
}
