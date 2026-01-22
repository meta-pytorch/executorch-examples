/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

/**
 * Holds module-specific settings for the current model/tokenizer configuration.
 */
data class ModuleSettings(
    val modelFilePath: String = "",
    val tokenizerFilePath: String = "",
    val dataPath: String = "",
    val temperature: Double = DEFAULT_TEMPERATURE,
    val systemPrompt: String = "",
    val userPrompt: String = PromptFormat.getUserPromptTemplate(DEFAULT_MODEL),
    val modelType: ModelType = DEFAULT_MODEL,
    val backendType: BackendType = DEFAULT_BACKEND,
    val isClearChatHistory: Boolean = false,
    val isLoadModel: Boolean = false
) {
    fun getFormattedSystemPrompt(): String {
        return PromptFormat.getSystemPromptTemplate(modelType)
            .replace(PromptFormat.SYSTEM_PLACEHOLDER, systemPrompt)
    }

    fun getFormattedUserPrompt(prompt: String, thinkingMode: Boolean): String {
        return userPrompt
            .replace(PromptFormat.USER_PLACEHOLDER, prompt)
            .replace(
                PromptFormat.THINKING_MODE_PLACEHOLDER,
                PromptFormat.getThinkingModeToken(modelType, thinkingMode)
            )
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.0
        val DEFAULT_MODEL = ModelType.LLAMA_3
        val DEFAULT_BACKEND = BackendType.XNNPACK
    }
}
