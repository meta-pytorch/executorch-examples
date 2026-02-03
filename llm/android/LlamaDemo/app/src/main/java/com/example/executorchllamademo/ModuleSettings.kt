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
 * Supports both legacy single-model and multi-model configurations for LoRA support.
 */
data class ModuleSettings(
    // Legacy single-model fields (kept for backward compatibility)
    val modelFilePath: String = "",
    val tokenizerFilePath: String = "",
    val dataPath: String = "",
    val temperature: Double = DEFAULT_TEMPERATURE,
    val systemPrompt: String = "",
    val userPrompt: String = PromptFormat.getUserPromptTemplate(DEFAULT_MODEL),
    val modelType: ModelType = DEFAULT_MODEL,
    val backendType: BackendType = DEFAULT_BACKEND,
    val isClearChatHistory: Boolean = false,
    val isLoadModel: Boolean = false,

    // LoRA mode toggle - when enabled, allows multiple model selection
    val isLoraMode: Boolean = false,

    // Foundation PTD path - shared base weights for all LoRA models
    val foundationDataPath: String = "",

    // Multi-model support fields (used when isLoraMode is true)
    val models: List<ModelConfiguration> = emptyList(),
    val activeModelId: String = "",
    val sharedDataPath: String = "",
    val foundationModelType: ModelType = ModelType.LLAMA_3
) {
    /**
     * Gets the effective model type, considering multi-model configuration.
     */
    fun getEffectiveModelType(): ModelType {
        val activeModel = getActiveModel()
        return activeModel?.modelType ?: modelType
    }

    fun getFormattedSystemPrompt(): String {
        return PromptFormat.getSystemPromptTemplate(getEffectiveModelType())
            .replace(PromptFormat.SYSTEM_PLACEHOLDER, systemPrompt)
    }

    fun getFormattedUserPrompt(prompt: String, thinkingMode: Boolean): String {
        val effectiveType = getEffectiveModelType()
        return userPrompt
            .replace(PromptFormat.USER_PLACEHOLDER, prompt)
            .replace(
                PromptFormat.THINKING_MODE_PLACEHOLDER,
                PromptFormat.getThinkingModeToken(effectiveType, thinkingMode)
            )
    }

    /**
     * Gets the active model configuration if using multi-model mode.
     */
    fun getActiveModel(): ModelConfiguration? {
        if (models.isEmpty() || activeModelId.isEmpty()) return null
        return models.find { it.id == activeModelId }
    }

    /**
     * Gets a model configuration by ID.
     */
    fun getModelById(modelId: String): ModelConfiguration? {
        return models.find { it.id == modelId }
    }

    /**
     * Gets the effective foundation data path for LoRA mode.
     * Priority: foundationDataPath > sharedDataPath > dataPath
     */
    fun getEffectiveDataPath(): String {
        return foundationDataPath.ifEmpty { sharedDataPath.ifEmpty { dataPath } }
    }

    /**
     * Checks if there are multiple models configured.
     */
    fun hasMultipleModels(): Boolean = models.size > 1

    /**
     * Checks if any models are configured.
     */
    fun hasModels(): Boolean = models.isNotEmpty()

    /**
     * Adds a model to the list. If a model with the same ID exists, it's replaced.
     * If this is the first model, it becomes the active model.
     */
    fun addModel(model: ModelConfiguration): ModuleSettings {
        val existingIndex = models.indexOfFirst { it.id == model.id }
        val newModels = if (existingIndex >= 0) {
            models.toMutableList().apply { this[existingIndex] = model }
        } else {
            models + model
        }
        val newActiveId = if (models.isEmpty()) model.id else activeModelId
        return copy(models = newModels, activeModelId = newActiveId)
    }

    /**
     * Removes a model by ID. If the active model is removed, selects another.
     */
    fun removeModel(modelId: String): ModuleSettings {
        val newModels = models.filter { it.id != modelId }
        val newActiveId = if (activeModelId == modelId) {
            newModels.firstOrNull()?.id ?: ""
        } else {
            activeModelId
        }
        return copy(models = newModels, activeModelId = newActiveId)
    }

    /**
     * Sets the active model by ID.
     */
    fun setActiveModel(modelId: String): ModuleSettings {
        return copy(activeModelId = modelId)
    }

    /**
     * Migrates legacy single-model settings to multi-model format.
     */
    fun migrateToMultiModel(): ModuleSettings {
        if (models.isNotEmpty()) return this

        // Only migrate if there's a valid legacy model configuration
        if (modelFilePath.isEmpty() || tokenizerFilePath.isEmpty()) return this

        val legacyModel = ModelConfiguration.create(
            modelFilePath = modelFilePath,
            tokenizerFilePath = tokenizerFilePath,
            modelType = modelType,
            backendType = backendType,
            temperature = temperature
        )

        return copy(
            models = listOf(legacyModel),
            activeModelId = legacyModel.id,
            sharedDataPath = dataPath
        )
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 0.0
        val DEFAULT_MODEL = ModelType.LLAMA_3
        val DEFAULT_BACKEND = BackendType.XNNPACK
    }
}
