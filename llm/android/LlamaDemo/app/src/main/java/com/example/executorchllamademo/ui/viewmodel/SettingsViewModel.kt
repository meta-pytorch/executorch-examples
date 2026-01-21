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
import com.example.executorchllamademo.BackendType
import com.example.executorchllamademo.DemoSharedPreferences
import com.example.executorchllamademo.ETLogging
import com.example.executorchllamademo.ModelType
import com.example.executorchllamademo.PromptFormat
import com.example.executorchllamademo.SettingsActivity
import com.example.executorchllamademo.SettingsFields
import com.google.gson.Gson

class SettingsViewModel : ViewModel() {

    var settingsFields by mutableStateOf(SettingsFields())
        private set

    // Dialog states
    var showBackendDialog by mutableStateOf(false)
    var showModelDialog by mutableStateOf(false)
    var showTokenizerDialog by mutableStateOf(false)
    var showDataPathDialog by mutableStateOf(false)
    var showModelTypeDialog by mutableStateOf(false)
    var showLoadModelDialog by mutableStateOf(false)
    var showClearChatDialog by mutableStateOf(false)
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
        val gson = Gson()
        val settingsFieldsJSON = demoSharedPreferences?.getSettings() ?: ""
        if (settingsFieldsJSON.isNotEmpty()) {
            settingsFields = gson.fromJson(settingsFieldsJSON, SettingsFields::class.java)
        }
    }

    fun saveSettings() {
        demoSharedPreferences?.addSettings(settingsFields)
    }

    fun refreshFileLists() {
        modelFiles = SettingsActivity.listLocalFile("/data/local/tmp/llama/", arrayOf(".pte"))
        tokenizerFiles = SettingsActivity.listLocalFile("/data/local/tmp/llama/", arrayOf(".bin", ".json", ".model"))
        dataPathFiles = SettingsActivity.listLocalFile("/data/local/tmp/llama/", arrayOf(".ptd"))
    }

    // Backend selection
    fun selectBackend(backendType: BackendType) {
        val newSettings = SettingsFields(settingsFields)
        newSettings.saveBackendType(backendType)
        applyBackendDefaults(newSettings)
        settingsFields = newSettings
    }

    private fun applyBackendDefaults(settings: SettingsFields) {
        if (settings.backendType == BackendType.MEDIATEK) {
            if (settings.modelFilePath.isEmpty()) {
                settings.saveModelPath("/in/mtk/llama/runner")
            }
            if (settings.tokenizerFilePath.isEmpty()) {
                settings.saveTokenizerPath("/in/mtk/llama/runner")
            }
        }
    }

    // Model selection
    fun selectModel(modelPath: String) {
        val newSettings = SettingsFields(settingsFields)
        newSettings.saveModelPath(modelPath)
        autoSelectModelType(newSettings, modelPath)
        settingsFields = newSettings
    }

    private fun autoSelectModelType(settings: SettingsFields, filePath: String) {
        val detectedType = ModelType.fromFilePath(filePath)
        if (detectedType != null) {
            settings.saveModelType(detectedType)
            settings.savePrompts(
                settings.systemPrompt,
                PromptFormat.getUserPromptTemplate(detectedType)
            )
        }
    }

    // Tokenizer selection
    fun selectTokenizer(tokenizerPath: String) {
        val newSettings = SettingsFields(settingsFields)
        newSettings.saveTokenizerPath(tokenizerPath)
        settingsFields = newSettings
    }

    // Data path selection
    fun selectDataPath(dataPath: String) {
        val newSettings = SettingsFields(settingsFields)
        newSettings.saveDataPath(dataPath)
        settingsFields = newSettings
    }

    // Model type selection
    fun selectModelType(modelType: ModelType) {
        val newSettings = SettingsFields(settingsFields)
        newSettings.saveModelType(modelType)
        newSettings.savePrompts(
            newSettings.systemPrompt,
            PromptFormat.getUserPromptTemplate(modelType)
        )
        settingsFields = newSettings
    }

    // Temperature
    fun updateTemperature(temperature: Double) {
        val newSettings = SettingsFields(settingsFields)
        newSettings.saveParameters(temperature)
        newSettings.saveLoadModelAction(true)
        settingsFields = newSettings
        saveSettings()
    }

    // System prompt
    fun updateSystemPrompt(prompt: String) {
        val newSettings = SettingsFields(settingsFields)
        newSettings.savePrompts(prompt, newSettings.userPrompt)
        settingsFields = newSettings
    }

    fun resetSystemPrompt() {
        val newSettings = SettingsFields(settingsFields)
        newSettings.savePrompts(PromptFormat.DEFAULT_SYSTEM_PROMPT, newSettings.userPrompt)
        settingsFields = newSettings
    }

    // User prompt
    fun updateUserPrompt(prompt: String) {
        if (isValidUserPrompt(prompt)) {
            val newSettings = SettingsFields(settingsFields)
            newSettings.savePrompts(newSettings.systemPrompt, prompt)
            settingsFields = newSettings
        } else {
            showInvalidPromptDialog = true
        }
    }

    fun resetUserPrompt() {
        val newSettings = SettingsFields(settingsFields)
        newSettings.savePrompts(
            newSettings.systemPrompt,
            PromptFormat.getUserPromptTemplate(newSettings.modelType)
        )
        settingsFields = newSettings
    }

    private fun isValidUserPrompt(userPrompt: String): Boolean {
        return userPrompt.contains(PromptFormat.USER_PLACEHOLDER)
    }

    // Load model action
    fun confirmLoadModel() {
        saveSettings()
        val newSettings = SettingsFields(settingsFields)
        newSettings.saveLoadModelAction(true)
        settingsFields = newSettings
    }

    // Clear chat
    fun confirmClearChat() {
        val newSettings = SettingsFields(settingsFields)
        newSettings.saveIsClearChatHistory(true)
        settingsFields = newSettings
    }

    // Validation
    fun isLoadModelEnabled(): Boolean {
        return settingsFields.modelFilePath.isNotEmpty() && settingsFields.tokenizerFilePath.isNotEmpty()
    }

    fun isMediaTekMode(): Boolean {
        return settingsFields.backendType == BackendType.MEDIATEK
    }

    fun getFilenameFromPath(path: String): String {
        return if (path.isEmpty()) "" else path.substringAfterLast('/')
    }

    // Appearance mode selection
    fun selectAppearanceMode(mode: AppearanceMode) {
        val newSettings = SettingsFields(settingsFields)
        newSettings.saveAppearanceMode(mode)
        settingsFields = newSettings
        saveSettings()
    }
}
