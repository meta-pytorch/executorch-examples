/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.executorchllamademo.BackendType
import com.example.executorchllamademo.R
import com.example.executorchllamademo.ui.components.*
import com.example.executorchllamademo.ui.theme.*
import com.example.executorchllamademo.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onLoadModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsFields by viewModel.settingsFields
    val isLoadModelEnabled by viewModel.isLoadModelEnabled
    val modelFiles by viewModel.modelFiles
    val tokenizerFiles by viewModel.tokenizerFiles
    val dataFiles by viewModel.dataFiles
    
    val showModelDialog by viewModel.showModelDialog
    val showTokenizerDialog by viewModel.showTokenizerDialog
    val showDataPathDialog by viewModel.showDataPathDialog
    val showSystemPromptDialog by viewModel.showSystemPromptDialog
    val showUserPromptDialog by viewModel.showUserPromptDialog
    val showLoadModelDialog by viewModel.showLoadModelDialog
    val showClearHistoryDialog by viewModel.showClearHistoryDialog
    
    // Dialogs
    if (showModelDialog) {
        FileSelectionDialog(
            title = "Select model path",
            files = modelFiles,
            onDismiss = { viewModel.showModelDialog.value = false },
            onFileSelected = { viewModel.selectModelFile(it) }
        )
    }
    
    if (showTokenizerDialog) {
        FileSelectionDialog(
            title = "Select tokenizer path",
            files = tokenizerFiles,
            onDismiss = { viewModel.showTokenizerDialog.value = false },
            onFileSelected = { viewModel.selectTokenizerFile(it) }
        )
    }
    
    if (showDataPathDialog) {
        FileSelectionDialog(
            title = "Select data path",
            files = dataFiles,
            onDismiss = { viewModel.showDataPathDialog.value = false },
            onFileSelected = { viewModel.selectDataFile(it) }
        )
    }
    
    if (showSystemPromptDialog) {
        TextInputDialog(
            title = "System Prompt",
            initialValue = settingsFields.systemPrompt,
            onDismiss = { viewModel.showSystemPromptDialog.value = false },
            onConfirm = { viewModel.updateSystemPrompt(it) }
        )
    }
    
    if (showUserPromptDialog) {
        TextInputDialog(
            title = "User Prompt",
            initialValue = settingsFields.userPrompt,
            onDismiss = { viewModel.showUserPromptDialog.value = false },
            onConfirm = { viewModel.updateUserPrompt(it) }
        )
    }
    
    if (showLoadModelDialog) {
        ConfirmationDialog(
            title = "Load Model",
            message = "Are you sure you want to load the selected model? This will replace the currently loaded model.",
            onDismiss = { viewModel.showLoadModelDialog.value = false },
            onConfirm = {
                viewModel.showLoadModelDialog.value = false
                viewModel.setLoadModelFlag(true)
                viewModel.saveSettings()
                onLoadModel()
            }
        )
    }
    
    if (showClearHistoryDialog) {
        ConfirmationDialog(
            title = "Clear Chat History",
            message = "Are you sure you want to clear all chat history?",
            onDismiss = { viewModel.showClearHistoryDialog.value = false },
            onConfirm = {
                viewModel.showClearHistoryDialog.value = false
                viewModel.setClearHistoryFlag(true)
                viewModel.saveSettings()
                onNavigateBack()
            }
        )
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.saveSettings()
                        onNavigateBack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_close_24),
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavBar
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Model Section
            SettingsSection(title = "Model") {
                FilePickerRow(
                    label = "Model",
                    value = viewModel.getModelFileName(),
                    onClick = { viewModel.showModelDialog.value = true },
                    textViewTestTag = "modelTextView",
                    buttonTestTag = "modelImageButton"
                )
                
                SettingsDivider()
                
                FilePickerRow(
                    label = "Tokenizer",
                    value = viewModel.getTokenizerFileName(),
                    onClick = { viewModel.showTokenizerDialog.value = true },
                    textViewTestTag = "tokenizerTextView",
                    buttonTestTag = "tokenizerImageButton"
                )
                
                SettingsDivider()
                
                FilePickerRow(
                    label = "Data Path",
                    value = viewModel.getDataFileName(),
                    onClick = { viewModel.showDataPathDialog.value = true }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Backend Section
            SettingsSection(title = "Backend") {
                BackendSelector(
                    selectedBackend = settingsFields.backendType,
                    onBackendSelected = { viewModel.updateBackendType(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Parameters Section
            SettingsSection(title = "Parameters") {
                TemperatureSlider(
                    value = settingsFields.temperature.toFloat(),
                    onValueChange = { viewModel.updateTemperature(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Prompts Section
            SettingsSection(title = "Prompts") {
                SettingsRow(
                    label = "System Prompt",
                    value = settingsFields.systemPrompt.take(50).let { 
                        if (settingsFields.systemPrompt.length > 50) "$it..." else it 
                    },
                    onClick = { viewModel.showSystemPromptDialog.value = true }
                )
                
                SettingsDivider()
                
                SettingsRow(
                    label = "User Prompt",
                    value = settingsFields.userPrompt.take(50).let { 
                        if (settingsFields.userPrompt.length > 50) "$it..." else it 
                    },
                    onClick = { viewModel.showUserPromptDialog.value = true }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action Buttons
            ActionButton(
                text = "Load Model",
                onClick = { viewModel.showLoadModelDialog.value = true },
                enabled = isLoadModelEnabled,
                testTag = "loadModelButton"
            )
            
            ActionButton(
                text = "Clear Chat History",
                onClick = { viewModel.showClearHistoryDialog.value = true }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BackendSelector(
    selectedBackend: BackendType,
    onBackendSelected: (BackendType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BackendType.values().forEach { backend ->
            FilterChip(
                selected = selectedBackend == backend,
                onClick = { onBackendSelected(backend) },
                label = { Text(backend.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary,
                    selectedLabelColor = TextOnPrimary
                )
            )
        }
    }
}
