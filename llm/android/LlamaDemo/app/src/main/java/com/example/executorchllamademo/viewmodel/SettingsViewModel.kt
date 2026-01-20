/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.viewmodel

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.example.executorchllamademo.*
import com.google.gson.Gson
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sharedPreferences = DemoSharedPreferences(context)
    
    // Settings state
    val settingsFields = mutableStateOf(SettingsFields())
    val modelFiles = mutableStateOf<List<String>>(emptyList())
    val tokenizerFiles = mutableStateOf<List<String>>(emptyList())
    val dataFiles = mutableStateOf<List<String>>(emptyList())
    
    // Dialog state
    val showModelDialog = mutableStateOf(false)
    val showTokenizerDialog = mutableStateOf(false)
    val showDataPathDialog = mutableStateOf(false)
    val showSystemPromptDialog = mutableStateOf(false)
    val showUserPromptDialog = mutableStateOf(false)
    val showLoadModelDialog = mutableStateOf(false)
    val showClearHistoryDialog = mutableStateOf(false)
    
    // Validation
    val isLoadModelEnabled = mutableStateOf(false)
    
    private val modelDirectory = File("/data/local/tmp/llama")
    
    init {
        loadSettings()
        refreshFilesList()
    }
    
    private fun loadSettings() {
        val settingsJson = sharedPreferences.getSettings()
        if (settingsJson.isNotEmpty()) {
            try {
                val gson = Gson()
                settingsFields.value = gson.fromJson(settingsJson, SettingsFields::class.java)
            } catch (e: Exception) {
                ETLogging.getInstance().log("Error loading settings: ${e.message}")
            }
        }
        updateLoadModelEnabled()
    }
    
    fun refreshFilesList() {
        if (modelDirectory.exists() && modelDirectory.isDirectory) {
            val allFiles = modelDirectory.listFiles()?.map { it.name } ?: emptyList()
            modelFiles.value = allFiles.filter { it.endsWith(".pte") }
            tokenizerFiles.value = allFiles.filter { 
                it.endsWith(".model") || it.endsWith(".bin") || it.endsWith(".json")
            }
            dataFiles.value = allFiles.filter { 
                !it.endsWith(".pte") && !it.endsWith(".model") && !it.endsWith(".bin")
            }
        } else {
            modelFiles.value = emptyList()
            tokenizerFiles.value = emptyList()
            dataFiles.value = emptyList()
        }
    }
    
    fun selectModelFile(fileName: String) {
        val fullPath = File(modelDirectory, fileName).absolutePath
        settingsFields.value = settingsFields.value.copy(modelFilePath = fullPath)
        
        // Auto-detect model type
        ModelType.fromFilePath(fileName)?.let { modelType ->
            settingsFields.value = settingsFields.value.copy(
                modelType = modelType,
                userPrompt = PromptFormat.getUserPromptTemplate(modelType)
            )
        }
        
        showModelDialog.value = false
        updateLoadModelEnabled()
    }
    
    fun selectTokenizerFile(fileName: String) {
        val fullPath = File(modelDirectory, fileName).absolutePath
        settingsFields.value = settingsFields.value.copy(tokenizerFilePath = fullPath)
        showTokenizerDialog.value = false
        updateLoadModelEnabled()
    }
    
    fun selectDataFile(fileName: String) {
        val fullPath = File(modelDirectory, fileName).absolutePath
        settingsFields.value = settingsFields.value.copy(dataPath = fullPath)
        showDataPathDialog.value = false
    }
    
    fun updateTemperature(temp: Float) {
        settingsFields.value = settingsFields.value.copy(temperature = temp.toDouble())
    }
    
    fun updateSystemPrompt(prompt: String) {
        settingsFields.value = settingsFields.value.copy(systemPrompt = prompt)
        showSystemPromptDialog.value = false
    }
    
    fun updateUserPrompt(prompt: String) {
        if (isValidUserPrompt(prompt)) {
            settingsFields.value = settingsFields.value.copy(userPrompt = prompt)
        }
        showUserPromptDialog.value = false
    }
    
    private fun isValidUserPrompt(prompt: String): Boolean {
        return prompt.contains(PromptFormat.USER_PLACEHOLDER)
    }
    
    fun updateBackendType(backendType: BackendType) {
        settingsFields.value = settingsFields.value.copy(backendType = backendType)
    }
    
    fun setLoadModelFlag(shouldLoad: Boolean) {
        settingsFields.value = settingsFields.value.copy(isLoadModel = shouldLoad)
    }
    
    fun setClearHistoryFlag(shouldClear: Boolean) {
        settingsFields.value = settingsFields.value.copy(isClearChatHistory = shouldClear)
    }
    
    private fun updateLoadModelEnabled() {
        isLoadModelEnabled.value = settingsFields.value.modelFilePath.isNotEmpty() &&
                settingsFields.value.tokenizerFilePath.isNotEmpty()
    }
    
    fun saveSettings() {
        sharedPreferences.addSettings(settingsFields.value)
    }
    
    fun getModelFileName(): String {
        val path = settingsFields.value.modelFilePath
        return if (path.isNotEmpty()) File(path).name else ""
    }
    
    fun getTokenizerFileName(): String {
        val path = settingsFields.value.tokenizerFilePath
        return if (path.isNotEmpty()) File(path).name else ""
    }
    
    fun getDataFileName(): String {
        val path = settingsFields.value.dataPath
        return if (path.isNotEmpty()) File(path).name else ""
    }
    
    companion object {
        const val TEMPERATURE_MIN_VALUE = 0.0
        const val TEMPERATURE_MAX_VALUE = 1.0
    }
}
