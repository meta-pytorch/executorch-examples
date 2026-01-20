/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.executorchllamademo.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule

class ChatViewModel(application: Application) : AndroidViewModel(application), LlmCallback {

    private val context = application.applicationContext
    private val sharedPreferences = DemoSharedPreferences(context)
    
    // UI State
    val messages = mutableStateListOf<Message>()
    val inputText = mutableStateOf("")
    val isGenerating = mutableStateOf(false)
    val isModelLoaded = mutableStateOf(false)
    val isThinkingMode = mutableStateOf(false)
    val memoryUsage = mutableStateOf("")
    val currentImageUri = mutableStateOf<Uri?>(null)
    
    // Settings
    var settingsFields = mutableStateOf(SettingsFields())
        private set
    
    // Model
    private var llmModule: LlmModule? = null
    private var currentPromptId = 0
    private var currentResponseMessage: Message? = null
    private var generationStartTime = 0L
    
    init {
        loadSavedMessages()
        loadSettings()
    }
    
    private fun loadSavedMessages() {
        val savedMessagesJson = sharedPreferences.getSavedMessages()
        if (savedMessagesJson.isNotEmpty()) {
            try {
                val gson = Gson()
                val type = object : TypeToken<ArrayList<Message>>() {}.type
                val savedMessages: ArrayList<Message>? = gson.fromJson(savedMessagesJson, type)
                savedMessages?.let {
                    messages.addAll(it)
                    currentPromptId = it.maxOfOrNull { msg -> msg.promptID } ?: 0
                }
            } catch (e: Exception) {
                ETLogging.getInstance().log("Error loading saved messages: ${e.message}")
            }
        }
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
    }
    
    fun updateSettings(newSettings: SettingsFields) {
        settingsFields.value = newSettings
        sharedPreferences.addSettings(newSettings)
    }
    
    fun loadModel(
        modelPath: String,
        tokenizerPath: String,
        temperature: Float,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            addSystemMessage("Loading model...")
            
            try {
                withContext(Dispatchers.IO) {
                    llmModule?.stop()
                    llmModule = LlmModule(modelPath, tokenizerPath, temperature)
                    val status = llmModule?.load() ?: -1
                    
                    withContext(Dispatchers.Main) {
                        if (status == 0) {
                            isModelLoaded.value = true
                            addSystemMessage("Successfully loaded model")
                            onResult(true, "Model loaded successfully")
                        } else {
                            addSystemMessage("Failed to load model (status: $status)")
                            onResult(false, "Failed to load model (status: $status)")
                        }
                    }
                }
            } catch (e: Exception) {
                addSystemMessage("Error loading model: ${e.message}")
                onResult(false, "Error: ${e.message}")
            }
        }
    }
    
    fun sendMessage() {
        val text = inputText.value.trim()
        if (text.isEmpty() && currentImageUri.value == null) return
        
        if (isGenerating.value) {
            stopGeneration()
            return
        }
        
        currentPromptId++
        
        // Add user message
        if (currentImageUri.value != null) {
            messages.add(Message.imageMessage(
                currentImageUri.value.toString(),
                isSent = true,
                promptID = currentPromptId
            ))
        }
        
        if (text.isNotEmpty()) {
            messages.add(Message.textMessage(text, isSent = true, promptID = currentPromptId))
        }
        
        // Clear input
        inputText.value = ""
        currentImageUri.value = null
        
        // Generate response
        generateResponse(text)
    }
    
    private fun generateResponse(prompt: String) {
        if (llmModule == null) {
            addSystemMessage("Please load a model first")
            return
        }
        
        isGenerating.value = true
        generationStartTime = System.currentTimeMillis()
        
        // Create response message
        currentResponseMessage = Message.textMessage("", isSent = false, promptID = currentPromptId)
        currentResponseMessage?.let { messages.add(it) }
        
        // Format prompt based on model type
        val formattedPrompt = settingsFields.value.getFormattedUserPrompt(prompt, isThinkingMode.value)
        val fullPrompt = if (settingsFields.value.systemPrompt.isNotEmpty()) {
            settingsFields.value.formattedSystemPrompt + formattedPrompt
        } else {
            formattedPrompt
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                llmModule?.generate(fullPrompt, this@ChatViewModel)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addSystemMessage("Generation error: ${e.message}")
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isGenerating.value = false
                    currentResponseMessage?.totalGenerationTime = 
                        System.currentTimeMillis() - generationStartTime
                    saveMessages()
                }
            }
        }
    }
    
    fun stopGeneration() {
        llmModule?.stop()
        isGenerating.value = false
    }
    
    override fun onResult(result: String) {
        viewModelScope.launch(Dispatchers.Main) {
            currentResponseMessage?.appendText(result)
            // Force recomposition by creating a new list reference
            val index = messages.indexOf(currentResponseMessage)
            if (index >= 0) {
                messages[index] = currentResponseMessage!!
            }
        }
    }
    
    override fun onStats(stats: String) {
        try {
            val jsonObject = JSONObject(stats)
            val numGeneratedTokens = jsonObject.getInt("generated_tokens")
            val inferenceEndMs = jsonObject.getInt("inference_end_ms")
            val promptEvalEndMs = jsonObject.getInt("prompt_eval_end_ms")
            val tps = numGeneratedTokens.toFloat() / (inferenceEndMs - promptEvalEndMs) * 1000
            
            viewModelScope.launch(Dispatchers.Main) {
                currentResponseMessage?.tokensPerSecond = tps
            }
        } catch (e: JSONException) {
            // Ignore parse errors
        }
    }
    
    fun addSystemMessage(text: String) {
        // Avoid duplicate system messages
        val lastMessage = messages.lastOrNull()
        if (lastMessage?.messageType == MessageType.SYSTEM && lastMessage.text == text) {
            return
        }
        messages.add(Message.systemMessage(text, currentPromptId))
    }
    
    fun clearMessages() {
        messages.clear()
        currentPromptId = 0
        sharedPreferences.removeExistingMessages()
    }
    
    fun saveMessages() {
        sharedPreferences.addMessages(messages.toList())
    }
    
    fun setInputText(text: String) {
        inputText.value = text
    }
    
    fun toggleThinkingMode() {
        isThinkingMode.value = !isThinkingMode.value
    }
    
    fun setImageUri(uri: Uri?) {
        currentImageUri.value = uri
    }
    
    override fun onCleared() {
        super.onCleared()
        saveMessages()
        llmModule?.stop()
    }
}
