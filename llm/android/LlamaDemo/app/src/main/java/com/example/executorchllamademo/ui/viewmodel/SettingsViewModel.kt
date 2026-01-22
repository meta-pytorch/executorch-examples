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
import com.example.executorchllamademo.ModelType
import com.example.executorchllamademo.ModuleSettings
import com.example.executorchllamademo.PromptFormat
import com.example.executorchllamademo.SettingsActivity

class SettingsViewModel : ViewModel() {

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
            moduleSettings = prefs.getModuleSettings()
            appSettings = prefs.getAppSettings()
        }
    }

    fun saveSettings() {
        demoSharedPreferences?.saveModuleSettings(moduleSettings)
        demoSharedPreferences?.saveAppSettings(appSettings)
    }

    fun refreshFileLists() {
        modelFiles = SettingsActivity.listLocalFile("/data/local/tmp/llama/", arrayOf(".pte"))
        tokenizerFiles = SettingsActivity.listLocalFile("/data/local/tmp/llama/", arrayOf(".bin", ".json", ".model"))
        dataPathFiles = SettingsActivity.listLocalFile("/data/local/tmp/llama/", arrayOf(".ptd"))
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

    // Model selection
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

    // Data path selection
    fun selectDataPath(dataPath: String) {
        moduleSettings = moduleSettings.copy(dataPath = dataPath)
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

    // Validation
    fun isLoadModelEnabled(): Boolean {
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
}
