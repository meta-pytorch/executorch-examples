/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

/**
 * Represents a single PTE model configuration.
 * Multiple ModelConfigurations can share the same PTD (data path) file for LoRA support.
 */
data class ModelConfiguration(
    val id: String = "",
    val modelFilePath: String = "",
    val tokenizerFilePath: String = "",
    val modelType: ModelType = ModelType.LLAMA_3,
    val backendType: BackendType = BackendType.XNNPACK,
    val temperature: Double = ModuleSettings.DEFAULT_TEMPERATURE,
    val displayName: String = "",
    val adapterFilePaths: List<String> = emptyList()
) {
    companion object {
        fun create(
            modelFilePath: String,
            tokenizerFilePath: String,
            modelType: ModelType,
            backendType: BackendType,
            temperature: Double
        ): ModelConfiguration {
            return ModelConfiguration(
                id = generateId(modelFilePath),
                modelFilePath = modelFilePath,
                tokenizerFilePath = tokenizerFilePath,
                modelType = modelType,
                backendType = backendType,
                temperature = temperature,
                displayName = extractDisplayName(modelFilePath)
            )
        }

        private fun generateId(modelFilePath: String): String {
            return modelFilePath.hashCode().toString()
        }

        private fun extractDisplayName(filePath: String): String {
            if (filePath.isEmpty()) return ""
            return filePath.substringAfterLast('/')
        }
    }

    fun isValid(): Boolean {
        return modelFilePath.isNotEmpty() && tokenizerFilePath.isNotEmpty()
    }

    fun withModelFilePath(path: String): ModelConfiguration {
        return copy(
            modelFilePath = path,
            id = generateId(path),
            displayName = extractDisplayName(path)
        )
    }
}
