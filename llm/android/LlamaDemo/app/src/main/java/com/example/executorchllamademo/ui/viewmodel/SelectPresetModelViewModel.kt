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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.executorchllamademo.DemoSharedPreferences
import com.example.executorchllamademo.ModelDownloadConfig
import com.example.executorchllamademo.ModelInfo
import com.example.executorchllamademo.ModuleSettings
import com.example.executorchllamademo.PromptFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ModelDownloadState(
    val isModelDownloaded: Boolean = false,
    val isTokenizerDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null
)

class SelectPresetModelViewModel : ViewModel() {

    private var context: Context? = null
    private var demoSharedPreferences: DemoSharedPreferences? = null

    val availableModels: Map<String, ModelInfo> = ModelDownloadConfig.getAvailableModels()

    // Track download state for each model
    val modelStates = mutableStateMapOf<String, ModelDownloadState>()

    var selectedModelKey by mutableStateOf<String?>(null)
        private set

    fun initialize(context: Context) {
        this.context = context
        demoSharedPreferences = DemoSharedPreferences(context)
        checkDownloadedFiles()
    }

    private fun getModelsDirectory(): File {
        val dir = File(context?.filesDir, "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun checkDownloadedFiles() {
        val modelsDir = getModelsDirectory()

        availableModels.forEach { (key, modelInfo) ->
            val modelFile = File(modelsDir, modelInfo.modelFilename)
            val tokenizerFile = File(modelsDir, modelInfo.tokenizerFilename)

            val currentState = modelStates[key] ?: ModelDownloadState()
            modelStates[key] = currentState.copy(
                isModelDownloaded = modelFile.exists(),
                isTokenizerDownloaded = tokenizerFile.exists()
            )
        }
    }

    fun isModelReady(key: String): Boolean {
        val state = modelStates[key] ?: return false
        return state.isModelDownloaded && state.isTokenizerDownloaded
    }

    fun needsDownload(key: String): Boolean {
        val state = modelStates[key] ?: return true
        return !state.isModelDownloaded || !state.isTokenizerDownloaded
    }

    fun isDownloading(key: String): Boolean {
        return modelStates[key]?.isDownloading == true
    }

    fun downloadModel(key: String) {
        val modelInfo = availableModels[key] ?: return
        val modelsDir = getModelsDirectory()

        val currentState = modelStates[key] ?: ModelDownloadState()
        modelStates[key] = currentState.copy(isDownloading = true, downloadError = null, downloadProgress = 0f)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Download model file if needed
                if (!currentState.isModelDownloaded) {
                    val modelFile = File(modelsDir, modelInfo.modelFilename)
                    downloadFile(modelInfo.modelUrl, modelFile) { progress ->
                        val state = modelStates[key] ?: ModelDownloadState()
                        modelStates[key] = state.copy(downloadProgress = progress * 0.5f)
                    }
                    withContext(Dispatchers.Main) {
                        val state = modelStates[key] ?: ModelDownloadState()
                        modelStates[key] = state.copy(isModelDownloaded = true)
                    }
                }

                // Download tokenizer file if needed
                if (!currentState.isTokenizerDownloaded && modelInfo.hasTokenizer()) {
                    val tokenizerFile = File(modelsDir, modelInfo.tokenizerFilename)
                    downloadFile(modelInfo.tokenizerUrl, tokenizerFile) { progress ->
                        val state = modelStates[key] ?: ModelDownloadState()
                        modelStates[key] = state.copy(downloadProgress = 0.5f + progress * 0.5f)
                    }
                    withContext(Dispatchers.Main) {
                        val state = modelStates[key] ?: ModelDownloadState()
                        modelStates[key] = state.copy(isTokenizerDownloaded = true)
                    }
                }

                withContext(Dispatchers.Main) {
                    val state = modelStates[key] ?: ModelDownloadState()
                    modelStates[key] = state.copy(isDownloading = false, downloadProgress = 1f)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val state = modelStates[key] ?: ModelDownloadState()
                    modelStates[key] = state.copy(
                        isDownloading = false,
                        downloadError = e.message ?: "Download failed"
                    )
                }
            }
        }
    }

    private fun downloadFile(urlString: String, outputFile: File, onProgress: (Float) -> Unit) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.connect()

        val contentLength = connection.contentLength
        var downloadedBytes = 0L

        connection.inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (contentLength > 0) {
                        onProgress(downloadedBytes.toFloat() / contentLength)
                    }
                }
            }
        }
    }

    fun loadModelAndStartChat(key: String): Boolean {
        val modelInfo = availableModels[key] ?: return false
        val modelsDir = getModelsDirectory()

        val modelFile = File(modelsDir, modelInfo.modelFilename)
        val tokenizerFile = File(modelsDir, modelInfo.tokenizerFilename)

        if (!modelFile.exists() || !tokenizerFile.exists()) {
            return false
        }

        // Save the module settings
        val moduleSettings = ModuleSettings(
            modelFilePath = modelFile.absolutePath,
            tokenizerFilePath = tokenizerFile.absolutePath,
            modelType = modelInfo.modelType,
            userPrompt = PromptFormat.getUserPromptTemplate(modelInfo.modelType),
            isLoadModel = true
        )

        demoSharedPreferences?.saveModuleSettings(moduleSettings)
        return true
    }

    fun deleteModel(key: String) {
        val modelInfo = availableModels[key] ?: return
        val modelsDir = getModelsDirectory()

        val modelFile = File(modelsDir, modelInfo.modelFilename)
        val tokenizerFile = File(modelsDir, modelInfo.tokenizerFilename)

        // Delete both files
        if (modelFile.exists()) {
            modelFile.delete()
        }
        if (tokenizerFile.exists()) {
            tokenizerFile.delete()
        }

        // Update state
        modelStates[key] = ModelDownloadState(
            isModelDownloaded = false,
            isTokenizerDownloaded = false,
            isDownloading = false,
            downloadProgress = 0f,
            downloadError = null
        )
    }
}
