/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

/**
 * Holds all settings fields for the application.
 *
 * Note: This is not a data class because it has mutable state and custom copy/save methods.
 */
class SettingsFields(
    var modelFilePath: String = "",
    var tokenizerFilePath: String = "",
    var dataPath: String = "",
    var temperature: Double = DEFAULT_TEMPERATURE,
    var systemPrompt: String = "",
    var userPrompt: String = PromptFormat.getUserPromptTemplate(DEFAULT_MODEL),
    var modelType: ModelType = DEFAULT_MODEL,
    var backendType: BackendType = DEFAULT_BACKEND,
    var appearanceMode: AppearanceMode = DEFAULT_APPEARANCE
) {
    /**
     * Copy constructor
     */
    constructor(other: SettingsFields) : this(
        modelFilePath = other.modelFilePath,
        tokenizerFilePath = other.tokenizerFilePath,
        dataPath = other.dataPath,
        temperature = other.temperature,
        systemPrompt = other.systemPrompt,
        userPrompt = other.userPrompt,
        modelType = other.modelType,
        backendType = other.backendType,
        appearanceMode = other.appearanceMode
    )

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

    // Save methods for backward compatibility with existing Java code
    fun saveModelPath(modelFilePath: String) {
        this.modelFilePath = modelFilePath
    }

    fun saveTokenizerPath(tokenizerFilePath: String) {
        this.tokenizerFilePath = tokenizerFilePath
    }

    fun saveModelType(modelType: ModelType) {
        this.modelType = modelType
    }

    fun saveBackendType(backendType: BackendType) {
        this.backendType = backendType
    }

    fun saveParameters(temperature: Double) {
        this.temperature = temperature
    }

    fun savePrompts(systemPrompt: String, userPrompt: String) {
        this.systemPrompt = systemPrompt
        this.userPrompt = userPrompt
    }

    fun saveDataPath(dataPath: String) {
        this.dataPath = dataPath
    }

    fun saveAppearanceMode(appearanceMode: AppearanceMode) {
        this.appearanceMode = appearanceMode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        other as SettingsFields
        return temperature.compareTo(other.temperature) == 0 &&
                modelFilePath == other.modelFilePath &&
                tokenizerFilePath == other.tokenizerFilePath &&
                dataPath == other.dataPath &&
                systemPrompt == other.systemPrompt &&
                userPrompt == other.userPrompt &&
                modelType == other.modelType &&
                backendType == other.backendType &&
                appearanceMode == other.appearanceMode
    }

    override fun hashCode(): Int {
        return listOf(
            modelFilePath,
            tokenizerFilePath,
            dataPath,
            temperature,
            systemPrompt,
            userPrompt,
            modelType,
            backendType,
            appearanceMode
        ).hashCode()
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.0
        private val DEFAULT_MODEL = ModelType.LLAMA_3
        private val DEFAULT_BACKEND = BackendType.XNNPACK
        private val DEFAULT_APPEARANCE = AppearanceMode.SYSTEM
    }
}
