/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

data class SettingsFields(
    var modelFilePath: String = "",
    var tokenizerFilePath: String = "",
    var dataPath: String = "",
    var temperature: Double = SettingsActivity.TEMPERATURE_MIN_VALUE,
    var systemPrompt: String = "",
    var userPrompt: String = PromptFormat.getUserPromptTemplate(DEFAULT_MODEL),
    var isClearChatHistory: Boolean = false,
    var isLoadModel: Boolean = false,
    var modelType: ModelType = DEFAULT_MODEL,
    var backendType: BackendType = DEFAULT_BACKEND
) {
    val formattedSystemPrompt: String
        get() = PromptFormat.getSystemPromptTemplate(modelType)
            .replace(PromptFormat.SYSTEM_PLACEHOLDER, systemPrompt)

    fun getFormattedUserPrompt(prompt: String, thinkingMode: Boolean): String {
        return userPrompt
            .replace(PromptFormat.USER_PLACEHOLDER, prompt)
            .replace(
                PromptFormat.THINKING_MODE_PLACEHOLDER,
                PromptFormat.getThinkingModeToken(modelType, thinkingMode)
            )
    }

    fun saveModelPath(path: String) {
        modelFilePath = path
    }

    fun saveTokenizerPath(path: String) {
        tokenizerFilePath = path
    }

    fun saveModelType(type: ModelType) {
        modelType = type
    }

    fun saveBackendType(type: BackendType) {
        backendType = type
    }

    fun saveParameters(temp: Double) {
        temperature = temp
    }

    fun savePrompts(system: String, user: String) {
        systemPrompt = system
        userPrompt = user
    }

    fun saveIsClearChatHistory(needToClear: Boolean) {
        isClearChatHistory = needToClear
    }

    fun saveLoadModelAction(shouldLoadModel: Boolean) {
        isLoadModel = shouldLoadModel
    }

    fun saveDataPath(path: String) {
        dataPath = path
    }

    companion object {
        private val DEFAULT_MODEL = ModelType.LLAMA_3
        private val DEFAULT_BACKEND = BackendType.XNNPACK
    }
}
