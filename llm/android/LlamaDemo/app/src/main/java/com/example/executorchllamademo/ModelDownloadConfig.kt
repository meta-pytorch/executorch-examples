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
    )

    fun getAvailableModels(): Map<String, ModelInfo> = AVAILABLE_MODELS

    fun getDisplayNames(): Array<String> =
        AVAILABLE_MODELS.values.map { it.displayName }.toTypedArray()

    fun getModelKeys(): Array<String> = AVAILABLE_MODELS.keys.toTypedArray()

    fun getByDisplayName(displayName: String): ModelInfo? =
        AVAILABLE_MODELS.values.find { it.displayName == displayName }

    fun getByKey(key: String): ModelInfo? = AVAILABLE_MODELS[key]
}
