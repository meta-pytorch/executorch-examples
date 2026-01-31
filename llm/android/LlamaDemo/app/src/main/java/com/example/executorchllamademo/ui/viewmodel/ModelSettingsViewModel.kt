/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.executorchllamademo.AppearanceMode
import com.example.executorchllamademo.AppSettings
import com.example.executorchllamademo.BackendType
import com.example.executorchllamademo.DemoSharedPreferences
import com.example.executorchllamademo.ModelConfiguration
import com.example.executorchllamademo.ModelSettingsActivity
import com.example.executorchllamademo.ModelType
import com.example.executorchllamademo.ModuleSettings
import com.example.executorchllamademo.PromptFormat

class ModelSettingsViewModel : ViewModel() {

    var moduleSettings by mutableStateOf(ModuleSettings())
        private set

    var appSettings by mutableStateOf(AppSettings())
        private set

    // Dialog states
    var showBackendDialog by mutableStateOf(false)
    var showModelDialog by mutableStateOf(false)
    var showTokenizerDialog by mutableStateOf(false)
    var showDataPathDialog by mutableStateOf(false)
    var showModelTypeDialog by mutableStateOf(false)
    var showLoadModelDialog by mutableStateOf(false)
    var showResetSystemPromptDialog by mutableStateOf(false)
    var showResetUserPromptDialog by mutableStateOf(false)
    var showInvalidPromptDialog by mutableStateOf(false)
    var showAppearanceDialog by mutableStateOf(false)
    var showAddModelDialog by mutableStateOf(false)
    var showRemoveModelDialog by mutableStateOf(false)
    var showMemoryWarningDialog by mutableStateOf(false)

    // Add model flow state
    var addModelStep by mutableStateOf(0)
        private set
    var tempModelPath by mutableStateOf("")
        private set
    var tempTokenizerPath by mutableStateOf("")
        private set
    var tempModelType by mutableStateOf(ModelType.LLAMA_3)
        private set

    // Model to be removed (for confirmation dialog)
    var modelToRemove by mutableStateOf<String?>(null)
        private set

    // File lists for dialogs
    var modelFiles by mutableStateOf<Array<String>>(emptyArray())
        private set
    var tokenizerFiles by mutableStateOf<Array<String>>(emptyArray())
        private set
    var dataPathFiles by mutableStateOf<Array<String>>(emptyArray())
        private set

    private var demoSharedPreferences: DemoSharedPreferences? = null

    fun initialize(context: Context) {
        demoSharedPreferences = DemoSharedPreferences(context)
        loadSettings()
        refreshFileLists()
    }

    private fun loadSettings() {
        demoSharedPreferences?.let { prefs ->
            var settings = prefs.getModuleSettings()
            // Only migrate to multi-model if LoRA mode is enabled
            if (settings.isLoraMode) {
                settings = settings.migrateToMultiModel()
            }
            moduleSettings = settings
            appSettings = prefs.getAppSettings()
        }
    }

    fun saveSettings() {
        demoSharedPreferences?.saveModuleSettings(moduleSettings)
        demoSharedPreferences?.saveAppSettings(appSettings)
    }

    fun refreshFileLists() {
        modelFiles = ModelSettingsActivity.listLocalFile("/data/local/tmp/llama/", arrayOf(".pte"))
        tokenizerFiles = ModelSettingsActivity.listLocalFile("/data/local/tmp/llama/", arrayOf(".bin", ".json", ".model"))
        dataPathFiles = ModelSettingsActivity.listLocalFile("/data/local/tmp/llama/", arrayOf(".ptd"))
    }

    // Backend selection
    fun selectBackend(backendType: BackendType) {
        var newSettings = moduleSettings.copy(backendType = backendType)
        newSettings = applyBackendDefaults(newSettings)
        moduleSettings = newSettings
    }

    private fun applyBackendDefaults(settings: ModuleSettings): ModuleSettings {
        return if (settings.backendType == BackendType.MEDIATEK) {
            settings.copy(
                modelFilePath = settings.modelFilePath.ifEmpty { "/in/mtk/llama/runner" },
                tokenizerFilePath = settings.tokenizerFilePath.ifEmpty { "/in/mtk/llama/runner" }
            )
        } else {
            settings
        }
    }

    // Model selection (legacy single-model mode)
    fun selectModel(modelPath: String) {
        var newSettings = moduleSettings.copy(modelFilePath = modelPath)
        newSettings = autoSelectModelType(newSettings, modelPath)
        moduleSettings = newSettings
    }

    private fun autoSelectModelType(settings: ModuleSettings, filePath: String): ModuleSettings {
        val detectedType = ModelType.fromFilePath(filePath)
        return if (detectedType != null) {
            settings.copy(
                modelType = detectedType,
                userPrompt = PromptFormat.getUserPromptTemplate(detectedType)
            )
        } else {
            settings
        }
    }

    // Tokenizer selection
    fun selectTokenizer(tokenizerPath: String) {
        moduleSettings = moduleSettings.copy(tokenizerFilePath = tokenizerPath)
    }

    // Data path selection (shared LoRA data path)
    fun selectDataPath(dataPath: String) {
        moduleSettings = moduleSettings.copy(
            dataPath = dataPath,
            sharedDataPath = dataPath
        )
    }

    // Model type selection
    fun selectModelType(modelType: ModelType) {
        moduleSettings = moduleSettings.copy(
            modelType = modelType,
            userPrompt = PromptFormat.getUserPromptTemplate(modelType)
        )
    }

    // Temperature
    fun updateTemperature(temperature: Double) {
        moduleSettings = moduleSettings.copy(
            temperature = temperature,
            isLoadModel = true
        )
        saveSettings()
    }

    // System prompt
    fun updateSystemPrompt(prompt: String) {
        moduleSettings = moduleSettings.copy(systemPrompt = prompt)
    }

    fun resetSystemPrompt() {
        moduleSettings = moduleSettings.copy(systemPrompt = PromptFormat.DEFAULT_SYSTEM_PROMPT)
    }

    // User prompt
    fun updateUserPrompt(prompt: String) {
        if (isValidUserPrompt(prompt)) {
            moduleSettings = moduleSettings.copy(userPrompt = prompt)
        } else {
            showInvalidPromptDialog = true
        }
    }

    fun resetUserPrompt() {
        moduleSettings = moduleSettings.copy(
            userPrompt = PromptFormat.getUserPromptTemplate(moduleSettings.modelType)
        )
    }

    private fun isValidUserPrompt(userPrompt: String): Boolean {
        return userPrompt.contains(PromptFormat.USER_PLACEHOLDER)
    }

    // Load model action
    fun confirmLoadModel() {
        saveSettings()
        moduleSettings = moduleSettings.copy(isLoadModel = true)
    }

    // Clear chat
    fun confirmClearChat() {
        moduleSettings = moduleSettings.copy(isClearChatHistory = true)
        saveSettings()
    }

    // Validation - considers both legacy and multi-model modes
    fun isLoadModelEnabled(): Boolean {
        // Check multi-model mode first
        if (moduleSettings.hasModels()) {
            return moduleSettings.models.any { it.isValid() }
        }
        // Fall back to legacy single-model mode
        return moduleSettings.modelFilePath.isNotEmpty() && moduleSettings.tokenizerFilePath.isNotEmpty()
    }

    fun isMediaTekMode(): Boolean {
        return moduleSettings.backendType == BackendType.MEDIATEK
    }

    fun getFilenameFromPath(path: String): String {
        return if (path.isEmpty()) "" else path.substringAfterLast('/')
    }

    // Appearance mode selection (app-wide setting)
    fun selectAppearanceMode(mode: AppearanceMode) {
        appSettings = appSettings.copy(appearanceMode = mode)
        demoSharedPreferences?.saveAppSettings(appSettings)
    }

    // ========== LoRA Mode Toggle ==========

    /**
     * Toggles LoRA mode on/off.
     * When enabled, allows multiple model selection.
     * When disabled, uses legacy single-model selection.
     */
    fun toggleLoraMode(enabled: Boolean) {
        moduleSettings = moduleSettings.copy(isLoraMode = enabled)
    }

    // ========== Multi-Model Support Methods ==========

    /**
     * Starts the add model flow.
     */
    fun startAddModel() {
        tempModelPath = ""
        tempTokenizerPath = ""
        tempModelType = ModelType.LLAMA_3
        addModelStep = 1
        showAddModelDialog = true
        refreshFileLists()
    }

    /**
     * Handles model selection in add model flow (step 1).
     */
    fun selectTempModel(modelPath: String) {
        tempModelPath = modelPath
        // Auto-detect model type
        val detectedType = ModelType.fromFilePath(modelPath)
        if (detectedType != null) {
            tempModelType = detectedType
        }
        addModelStep = 2
    }

    /**
     * Handles tokenizer selection in add model flow (step 2).
     */
    fun selectTempTokenizer(tokenizerPath: String) {
        tempTokenizerPath = tokenizerPath
        addModelStep = 3
    }

    /**
     * Handles model type confirmation in add model flow (step 3).
     */
    fun selectTempModelType(modelType: ModelType) {
        tempModelType = modelType
    }

    /**
     * Confirms and adds the new model.
     */
    fun confirmAddModel() {
        if (tempModelPath.isEmpty() || tempTokenizerPath.isEmpty()) return

        val newModel = ModelConfiguration.create(
            modelFilePath = tempModelPath,
            tokenizerFilePath = tempTokenizerPath,
            modelType = tempModelType,
            backendType = moduleSettings.backendType,
            temperature = ModuleSettings.DEFAULT_TEMPERATURE
        )

        moduleSettings = moduleSettings.addModel(newModel)
        cancelAddModel()
    }

    /**
     * Cancels the add model flow.
     */
    fun cancelAddModel() {
        showAddModelDialog = false
        addModelStep = 0
        tempModelPath = ""
        tempTokenizerPath = ""
        tempModelType = ModelType.LLAMA_3
    }

    /**
     * Goes back to previous step in add model flow.
     */
    fun previousAddModelStep() {
        when (addModelStep) {
            2 -> {
                addModelStep = 1
                tempTokenizerPath = ""
            }
            3 -> {
                addModelStep = 2
            }
            else -> cancelAddModel()
        }
    }

    /**
     * Selects a model as active.
     */
    fun selectActiveModel(modelId: String) {
        moduleSettings = moduleSettings.setActiveModel(modelId)
    }

    /**
     * Initiates model removal (shows confirmation).
     */
    fun initiateRemoveModel(modelId: String) {
        modelToRemove = modelId
        showRemoveModelDialog = true
    }

    /**
     * Confirms model removal.
     */
    fun confirmRemoveModel() {
        modelToRemove?.let { modelId ->
            moduleSettings = moduleSettings.removeModel(modelId)
        }
        cancelRemoveModel()
    }

    /**
     * Cancels model removal.
     */
    fun cancelRemoveModel() {
        showRemoveModelDialog = false
        modelToRemove = null
    }

    /**
     * Checks if load should show memory warning (more than 2 models).
     */
    fun shouldShowMemoryWarning(): Boolean {
        return moduleSettings.models.size > 2
    }

    /**
     * Initiates load models action (may show memory warning first).
     */
    fun initiateLoadModels() {
        if (shouldShowMemoryWarning()) {
            showMemoryWarningDialog = true
        } else {
            showLoadModelDialog = true
        }
    }

    /**
     * Proceeds with loading after memory warning.
     */
    fun proceedAfterMemoryWarning() {
        showMemoryWarningDialog = false
        showLoadModelDialog = true
    }
}
