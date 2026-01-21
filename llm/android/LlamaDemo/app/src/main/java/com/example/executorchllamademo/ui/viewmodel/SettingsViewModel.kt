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
        settingsFields.saveBackendType(backendType)
        settingsFields = SettingsFields(settingsFields) // Trigger recomposition
        applyBackendDefaults()
    }

    private fun applyBackendDefaults() {
        if (settingsFields.backendType == BackendType.MEDIATEK) {
            if (settingsFields.modelFilePath.isEmpty()) {
                settingsFields.saveModelPath("/in/mtk/llama/runner")
            }
            if (settingsFields.tokenizerFilePath.isEmpty()) {
                settingsFields.saveTokenizerPath("/in/mtk/llama/runner")
            }
            settingsFields = SettingsFields(settingsFields)
        }
    }

    // Model selection
    fun selectModel(modelPath: String) {
        settingsFields.saveModelPath(modelPath)
        autoSelectModelType(modelPath)
        settingsFields = SettingsFields(settingsFields)
    }

    private fun autoSelectModelType(filePath: String) {
        val detectedType = ModelType.fromFilePath(filePath)
        if (detectedType != null) {
            settingsFields.saveModelType(detectedType)
            settingsFields.savePrompts(
                settingsFields.systemPrompt,
                PromptFormat.getUserPromptTemplate(detectedType)
            )
        }
    }

    // Tokenizer selection
    fun selectTokenizer(tokenizerPath: String) {
        settingsFields.saveTokenizerPath(tokenizerPath)
        settingsFields = SettingsFields(settingsFields)
    }

    // Data path selection
    fun selectDataPath(dataPath: String) {
        settingsFields.saveDataPath(dataPath)
        settingsFields = SettingsFields(settingsFields)
    }

    // Model type selection
    fun selectModelType(modelType: ModelType) {
        settingsFields.saveModelType(modelType)
        settingsFields.savePrompts(
            settingsFields.systemPrompt,
            PromptFormat.getUserPromptTemplate(modelType)
        )
        settingsFields = SettingsFields(settingsFields)
    }

    // Temperature
    fun updateTemperature(temperature: Double) {
        settingsFields.saveParameters(temperature)
        settingsFields.saveLoadModelAction(true)
        settingsFields = SettingsFields(settingsFields)
        saveSettings()
    }

    // System prompt
    fun updateSystemPrompt(prompt: String) {
        settingsFields.savePrompts(prompt, settingsFields.userPrompt)
        settingsFields = SettingsFields(settingsFields)
    }

    fun resetSystemPrompt() {
        settingsFields.savePrompts(PromptFormat.DEFAULT_SYSTEM_PROMPT, settingsFields.userPrompt)
        settingsFields = SettingsFields(settingsFields)
    }

    // User prompt
    fun updateUserPrompt(prompt: String) {
        if (isValidUserPrompt(prompt)) {
            settingsFields.savePrompts(settingsFields.systemPrompt, prompt)
            settingsFields = SettingsFields(settingsFields)
        } else {
            showInvalidPromptDialog = true
        }
    }

    fun resetUserPrompt() {
        settingsFields.savePrompts(
            settingsFields.systemPrompt,
            PromptFormat.getUserPromptTemplate(settingsFields.modelType)
        )
        settingsFields = SettingsFields(settingsFields)
    }

    private fun isValidUserPrompt(userPrompt: String): Boolean {
        return userPrompt.contains(PromptFormat.USER_PLACEHOLDER)
    }

    // Load model action
    fun confirmLoadModel() {
        saveSettings()
        settingsFields.saveLoadModelAction(true)
        settingsFields = SettingsFields(settingsFields)
    }

    // Clear chat
    fun confirmClearChat() {
        settingsFields.saveIsClearChatHistory(true)
        settingsFields = SettingsFields(settingsFields)
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
}
