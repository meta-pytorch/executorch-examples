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
import androidx.compose.ui.res.stringResource
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
            title = stringResource(R.string.select_model_path),
            files = modelFiles,
            onDismiss = { viewModel.showModelDialog.value = false },
            onFileSelected = { viewModel.selectModelFile(it) }
        )
    }
    
    if (showTokenizerDialog) {
        FileSelectionDialog(
            title = stringResource(R.string.select_tokenizer_path),
            files = tokenizerFiles,
            onDismiss = { viewModel.showTokenizerDialog.value = false },
            onFileSelected = { viewModel.selectTokenizerFile(it) }
        )
    }
    
    if (showDataPathDialog) {
        FileSelectionDialog(
            title = stringResource(R.string.select_data_path),
            files = dataFiles,
            onDismiss = { viewModel.showDataPathDialog.value = false },
            onFileSelected = { viewModel.selectDataFile(it) }
        )
    }
    
    if (showSystemPromptDialog) {
        TextInputDialog(
            title = stringResource(R.string.system_prompt_title),
            initialValue = settingsFields.systemPrompt,
            onDismiss = { viewModel.showSystemPromptDialog.value = false },
            onConfirm = { viewModel.updateSystemPrompt(it) }
        )
    }
    
    if (showUserPromptDialog) {
        TextInputDialog(
            title = stringResource(R.string.user_prompt_title),
            initialValue = settingsFields.userPrompt,
            onDismiss = { viewModel.showUserPromptDialog.value = false },
            onConfirm = { viewModel.updateUserPrompt(it) }
        )
    }
    
    if (showLoadModelDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.load_model),
            message = stringResource(R.string.load_model_dialog_message),
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
            title = stringResource(R.string.clear_history),
            message = stringResource(R.string.clear_history_dialog_message),
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
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.saveSettings()
                            onNavigateBack()
                        },
                        modifier = Modifier.testTag("backButton")
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_close_24),
                            contentDescription = stringResource(R.string.back_description),
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
            SettingsSection(title = stringResource(R.string.select_model)) {
                FilePickerRow(
                    label = stringResource(R.string.select_model),
                    value = viewModel.getModelFileName(),
                    onClick = { viewModel.showModelDialog.value = true },
                    textViewTestTag = "modelTextView",
                    buttonTestTag = "modelImageButton"
                )
                
                SettingsDivider()
                
                FilePickerRow(
                    label = stringResource(R.string.select_tokenizer),
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

                SettingsDivider()

                Text(
                    text = "Model Type",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                ModelTypeSelector(
                    selectedType = settingsFields.modelType,
                    onTypeSelected = { viewModel.updateModelType(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Backend Section
            SettingsSection(title = stringResource(R.string.backend_section)) {
                BackendSelector(
                    selectedBackend = settingsFields.backendType,
                    onBackendSelected = { viewModel.updateBackendType(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Parameters Section
            SettingsSection(title = stringResource(R.string.parameters_section)) {
                TemperatureSlider(
                    value = settingsFields.temperature.toFloat(),
                    onValueChange = { viewModel.updateTemperature(it) }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Prompts Section
            SettingsSection(title = stringResource(R.string.prompts_section)) {
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
                text = stringResource(R.string.load_model),
                onClick = { viewModel.showLoadModelDialog.value = true },
                enabled = isLoadModelEnabled,
                testTag = "loadModelButton"
            )
            
            ActionButton(
                text = stringResource(R.string.clear_history),
                onClick = { viewModel.showClearHistoryDialog.value = true },
                testTag = "clearHistoryButton"
            )
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelTypeSelector(
    selectedType: com.example.executorchllamademo.ModelType,
    onTypeSelected: (com.example.executorchllamademo.ModelType) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        com.example.executorchllamademo.ModelType.values().forEach { type ->
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { Text(type.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Primary,
                    selectedLabelColor = TextOnPrimary
                )
            )
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
