/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.executorchllamademo.AppearanceMode
import com.example.executorchllamademo.BackendType
import com.example.executorchllamademo.ModelType
import com.example.executorchllamademo.PromptFormat
import com.example.executorchllamademo.ModelConfiguration
import com.example.executorchllamademo.ui.components.ModelListItem
import com.example.executorchllamademo.ui.components.SettingsRow
import com.example.executorchllamademo.ui.theme.BtnDisabled
import com.example.executorchllamademo.ui.theme.BtnEnabled
import com.example.executorchllamademo.ui.theme.LocalAppColors
import com.example.executorchllamademo.ui.viewmodel.ModelSettingsViewModel

@Composable
fun ModelSettingsScreen(
    viewModel: ModelSettingsViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
    onLoadModel: () -> Unit = {},
    onAppearanceChanged: (AppearanceMode) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.settingsBackground)
    ) {
        // Top banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.navBar)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = appColors.textOnNavBar
                )
            }
            Text(
                text = "Select a Model",
                color = appColors.textOnNavBar,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(12.dp)
        ) {
            // Backend selector
            SettingsRow(
                label = "Backend",
                value = viewModel.moduleSettings.backendType.toString(),
                onClick = { viewModel.showBackendDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ========== LoRA Mode Toggle ==========
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LoRA Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.settingsText
                    )
                    Text(
                        text = "Enable for multiple model selection",
                        fontSize = 12.sp,
                        color = appColors.settingsSecondaryText
                    )
                }
                androidx.compose.material3.Switch(
                    checked = viewModel.moduleSettings.isLoraMode,
                    onCheckedChange = { viewModel.toggleLoraMode(it) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ========== Conditional UI based on LoRA mode ==========
            if (viewModel.moduleSettings.isLoraMode) {
                // Foundation PTD selector (required for LoRA)
                SettingsRow(
                    label = "Foundation PTD",
                    value = viewModel.getFilenameFromPath(viewModel.moduleSettings.foundationDataPath)
                        .ifEmpty { "no foundation PTD selected" },
                    onClick = {
                        viewModel.refreshFileLists()
                        viewModel.showFoundationDataPathDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Foundation Model Type selector (for LoRA)
                SettingsRow(
                    label = "Foundation Model Type",
                    value = viewModel.moduleSettings.foundationModelType.toString(),
                    onClick = {
                        viewModel.showFoundationModelTypeDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // LoRA Mode: Multi-model selection
                Text(
                    text = "Models",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.settingsText
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Model list
                if (viewModel.moduleSettings.hasModels()) {
                    viewModel.moduleSettings.models.forEach { model ->
                        ModelListItem(
                            model = model,
                            isActive = model.id == viewModel.moduleSettings.activeModelId,
                            onSelect = { viewModel.selectActiveModel(model.id) },
                            onRemove = { viewModel.initiateRemoveModel(model.id) }
                        )
                    }
                } else {
                    Text(
                        text = "No models configured",
                        fontSize = 14.sp,
                        color = appColors.settingsSecondaryText,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Add Model button
                Button(
                    onClick = { viewModel.startAddModel() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BtnEnabled
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("+ Add Model", color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Load All Models button
                Button(
                    onClick = { viewModel.initiateLoadModels() },
                    enabled = viewModel.isLoadModelEnabled(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BtnEnabled,
                        disabledContainerColor = BtnDisabled
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (viewModel.moduleSettings.hasMultipleModels()) "Load All Models" else "Load Model",
                        color = Color.White
                    )
                }
            } else {
                // Normal Mode: Single-model selection (original UI)
                if (!viewModel.isMediaTekMode()) {
                    // Model selector
                    SettingsRow(
                        label = "Model",
                        value = viewModel.getFilenameFromPath(viewModel.moduleSettings.modelFilePath)
                            .ifEmpty { "no model selected" },
                        onClick = {
                            viewModel.refreshFileLists()
                            viewModel.showModelDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Tokenizer selector
                    SettingsRow(
                        label = "Tokenizer",
                        value = viewModel.getFilenameFromPath(viewModel.moduleSettings.tokenizerFilePath)
                            .ifEmpty { "no tokenizer selected" },
                        onClick = {
                            viewModel.refreshFileLists()
                            viewModel.showTokenizerDialog = true
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Data path selector
                    SettingsRow(
                        label = "Data Path",
                        value = viewModel.getFilenameFromPath(viewModel.moduleSettings.dataPath)
                            .ifEmpty { "no data path selected" },
                        onClick = {
                            viewModel.refreshFileLists()
                            viewModel.showDataPathDialog = true
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Model type selector
                SettingsRow(
                    label = "Model Type",
                    value = viewModel.moduleSettings.modelType.toString(),
                    onClick = { viewModel.showModelTypeDialog = true }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Load Model button
                Button(
                    onClick = { viewModel.showLoadModelDialog = true },
                    enabled = viewModel.isLoadModelEnabled(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BtnEnabled,
                        disabledContainerColor = BtnDisabled
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Load Model", color = Color.White)
                }
            }

            if (!viewModel.isMediaTekMode()) {
                Spacer(modifier = Modifier.height(24.dp))

                // Temperature with slider and input box
                Text(
                    text = "Parameters",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.settingsText
                )

                Spacer(modifier = Modifier.height(8.dp))

                var temperatureText by remember(viewModel.moduleSettings.temperature) {
                    mutableStateOf(String.format("%.2f", viewModel.moduleSettings.temperature))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Temperature",
                        fontSize = 14.sp,
                        color = appColors.settingsText
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Slider
                    Slider(
                        value = viewModel.moduleSettings.temperature.toFloat().coerceIn(0f, 2f),
                        onValueChange = { newValue ->
                            val rounded = (newValue * 100).toInt() / 100.0
                            temperatureText = String.format("%.2f", rounded)
                            viewModel.updateTemperature(rounded)
                        },
                        valueRange = 0f..2f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = BtnEnabled,
                            activeTrackColor = BtnEnabled
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Number input box
                    Box(
                        modifier = Modifier
                            .width(60.dp)
                            .border(1.dp, appColors.settingsSecondaryText, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        BasicTextField(
                            value = temperatureText,
                            onValueChange = { newValue ->
                                temperatureText = newValue
                                newValue.toDoubleOrNull()?.let {
                                    viewModel.updateTemperature(it.coerceIn(0.0, 2.0))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = appColors.settingsText)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Advanced Options section
            var showAdvancedOptions by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedOptions = !showAdvancedOptions }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced Options",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.settingsText,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = if (showAdvancedOptions) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = if (showAdvancedOptions) "Collapse" else "Expand",
                    tint = appColors.settingsText
                )
            }

            AnimatedVisibility(
                visible = showAdvancedOptions,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // System Prompt
                    PromptSection(
                        title = "System Prompt",
                        value = viewModel.moduleSettings.systemPrompt,
                        onValueChange = { viewModel.updateSystemPrompt(it) },
                        onReset = { viewModel.showResetSystemPromptDialog = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // User Prompt Format
                    PromptSection(
                        title = "Prompt Format",
                        value = viewModel.moduleSettings.userPrompt,
                        onValueChange = { viewModel.updateUserPrompt(it) },
                        onReset = { viewModel.showResetUserPromptDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Dialogs
    BackendDialog(viewModel)
    AppearanceDialog(viewModel, onAppearanceChanged)
    ModelDialog(viewModel)
    TokenizerDialog(viewModel)
    DataPathDialog(viewModel)
    FoundationDataPathDialog(viewModel)
    ModelTypeDialog(viewModel)
    LoadModelDialog(viewModel, onLoadModel, onBackPressed)
    ResetSystemPromptDialog(viewModel)
    ResetUserPromptDialog(viewModel)
    InvalidPromptDialog(viewModel)
    AddModelDialog(viewModel)
    RemoveModelDialog(viewModel)
    MemoryWarningDialog(viewModel)
}

@Composable
private fun PromptSection(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = appColors.settingsText,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onReset) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Reset $title",
                tint = appColors.settingsText
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(1.dp, appColors.settingsSecondaryText, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(fontSize = 14.sp, color = appColors.settingsText)
        )
    }
}

@Composable
private fun BackendDialog(viewModel: ModelSettingsViewModel) {
    if (viewModel.showBackendDialog) {
        SingleChoiceDialog(
            title = "Select backend type",
            options = BackendType.values().map { it.toString() },
            onSelect = { selected ->
                viewModel.selectBackend(BackendType.valueOf(selected))
                viewModel.showBackendDialog = false
            },
            onDismiss = { viewModel.showBackendDialog = false }
        )
    }
}

@Composable
private fun AppearanceDialog(
    viewModel: ModelSettingsViewModel,
    onAppearanceChanged: (AppearanceMode) -> Unit
) {
    if (viewModel.showAppearanceDialog) {
        SingleChoiceDialog(
            title = "Select appearance",
            options = AppearanceMode.values().map { it.displayName },
            onSelect = { selected ->
                val mode = AppearanceMode.fromDisplayName(selected)
                viewModel.selectAppearanceMode(mode)
                onAppearanceChanged(mode)
                viewModel.showAppearanceDialog = false
            },
            onDismiss = { viewModel.showAppearanceDialog = false }
        )
    }
}

@Composable
private fun ModelDialog(viewModel: ModelSettingsViewModel) {
    if (viewModel.showModelDialog) {
        if (viewModel.modelFiles.isEmpty()) {
            AlertDialog(
                onDismissRequest = { viewModel.showModelDialog = false },
                title = { Text("Select model path") },
                text = {
                    Text("No model files (.pte) found in /data/local/tmp/llama/\n\nPlease push model files using:\nadb push <model>.pte /data/local/tmp/llama/")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.showModelDialog = false }) {
                        Text("OK")
                    }
                }
            )
        } else {
            SingleChoiceDialog(
                title = "Select model path",
                options = viewModel.modelFiles.toList(),
                onSelect = { selected ->
                    viewModel.selectModel(selected)
                    viewModel.showModelDialog = false
                },
                onDismiss = { viewModel.showModelDialog = false }
            )
        }
    }
}

@Composable
private fun TokenizerDialog(viewModel: ModelSettingsViewModel) {
    if (viewModel.showTokenizerDialog) {
        if (viewModel.tokenizerFiles.isEmpty()) {
            AlertDialog(
                onDismissRequest = { viewModel.showTokenizerDialog = false },
                title = { Text("Select tokenizer path") },
                text = {
                    Text("No tokenizer files (.bin, .json, .model) found in /data/local/tmp/llama/\n\nPlease push tokenizer files using:\nadb push <tokenizer> /data/local/tmp/llama/")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.showTokenizerDialog = false }) {
                        Text("OK")
                    }
                }
            )
        } else {
            SingleChoiceDialog(
                title = "Select tokenizer path",
                options = viewModel.tokenizerFiles.toList(),
                onSelect = { selected ->
                    viewModel.selectTokenizer(selected)
                    viewModel.showTokenizerDialog = false
                },
                onDismiss = { viewModel.showTokenizerDialog = false }
            )
        }
    }
}

@Composable
private fun DataPathDialog(viewModel: ModelSettingsViewModel) {
    if (viewModel.showDataPathDialog) {
        val options = if (viewModel.dataPathFiles.isEmpty()) {
            listOf("(unused)")
        } else {
            viewModel.dataPathFiles.toList() + "(unused)"
        }

        SingleChoiceDialog(
            title = "Select data path",
            options = options,
            onSelect = { selected ->
                if (selected == "(unused)") {
                    viewModel.selectDataPath("")
                } else {
                    viewModel.selectDataPath(selected)
                }
                viewModel.showDataPathDialog = false
            },
            onDismiss = { viewModel.showDataPathDialog = false }
        )
    }
}

@Composable
private fun FoundationDataPathDialog(viewModel: ModelSettingsViewModel) {
    if (viewModel.showFoundationDataPathDialog) {
        if (viewModel.dataPathFiles.isEmpty()) {
            AlertDialog(
                onDismissRequest = { viewModel.showFoundationDataPathDialog = false },
                title = { Text("Select Foundation PTD") },
                text = {
                    Text("No PTD files (.ptd) found in /data/local/tmp/llama/\n\nPlease push foundation PTD file using:\nadb push <foundation>.ptd /data/local/tmp/llama/")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.showFoundationDataPathDialog = false }) {
                        Text("OK")
                    }
                }
            )
        } else {
            SingleChoiceDialog(
                title = "Select Foundation PTD",
                options = viewModel.dataPathFiles.toList(),
                onSelect = { selected ->
                    viewModel.selectFoundationDataPath(selected)
                    viewModel.showFoundationDataPathDialog = false
                },
                onDismiss = { viewModel.showFoundationDataPathDialog = false }
            )
        }
    }
}

@Composable
private fun ModelTypeDialog(viewModel: ModelSettingsViewModel) {
    if (viewModel.showModelTypeDialog) {
        SingleChoiceDialog(
            title = "Select model type",
            options = ModelType.values().map { it.toString() },
            onSelect = { selected ->
                viewModel.selectModelType(ModelType.valueOf(selected))
                viewModel.showModelTypeDialog = false
            },
            onDismiss = { viewModel.showModelTypeDialog = false }
        )
    }
}

@Composable
private fun LoadModelDialog(
    viewModel: ModelSettingsViewModel,
    onLoadModel: () -> Unit,
    onBackPressed: () -> Unit
) {
    if (viewModel.showLoadModelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showLoadModelDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null
                )
            },
            title = { Text("Load Model") },
            text = { Text("Do you really want to load the new model?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmLoadModel()
                        viewModel.showLoadModelDialog = false
                        onLoadModel()
                        onBackPressed()
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showLoadModelDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}

@Composable
private fun ResetSystemPromptDialog(viewModel: ModelSettingsViewModel) {
    if (viewModel.showResetSystemPromptDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showResetSystemPromptDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null
                )
            },
            title = { Text("Reset System Prompt") },
            text = { Text("Do you really want to reset system prompt?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetSystemPrompt()
                        viewModel.showResetSystemPromptDialog = false
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showResetSystemPromptDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}

@Composable
private fun ResetUserPromptDialog(viewModel: ModelSettingsViewModel) {
    if (viewModel.showResetUserPromptDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showResetUserPromptDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null
                )
            },
            title = { Text("Reset Prompt Template") },
            text = { Text("Do you really want to reset the prompt template?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetUserPrompt()
                        viewModel.showResetUserPromptDialog = false
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showResetUserPromptDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}

@Composable
private fun InvalidPromptDialog(viewModel: ModelSettingsViewModel) {
    if (viewModel.showInvalidPromptDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showInvalidPromptDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null
                )
            },
            title = { Text("Invalid Prompt Format") },
            text = {
                Text("Prompt format must contain ${PromptFormat.USER_PLACEHOLDER}. Do you want to reset prompt format?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetUserPrompt()
                        viewModel.showInvalidPromptDialog = false
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showInvalidPromptDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedOption = option
                                onSelect(option)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = null // Let the Row handle the click
                        )
                        Text(
                            text = option.substringAfterLast('/'),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddModelDialog(viewModel: ModelSettingsViewModel) {
    if (!viewModel.showAddModelDialog) return

    when (viewModel.addModelStep) {
        1 -> {
            // Step 1: Select model file
            if (viewModel.modelFiles.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { viewModel.cancelAddModel() },
                    title = { Text("Step 1: Select Model (.pte)") },
                    text = {
                        Text("No .pte files found in /data/local/tmp/llama/\n\nPlease push model files using:\nadb push <model>.pte /data/local/tmp/llama/")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.cancelAddModel() }) {
                            Text("OK")
                        }
                    }
                )
            } else {
                SingleChoiceDialogWithPreselection(
                    title = "Step 1: Select Model (.pte)",
                    options = viewModel.modelFiles.toList(),
                    selectedOption = null,
                    onSelect = { selected ->
                        viewModel.selectTempModel(selected)
                    },
                    onDismiss = { viewModel.cancelAddModel() },
                    dismissButtonText = "Cancel"
                )
            }
        }
        2 -> {
            // Step 2: Select tokenizer file
            if (viewModel.tokenizerFiles.isEmpty()) {
                AlertDialog(
                    onDismissRequest = { viewModel.previousAddModelStep() },
                    title = { Text("Step 2: Select Tokenizer") },
                    text = {
                        Text("No tokenizer files found in /data/local/tmp/llama/\n\nPlease push tokenizer files using:\nadb push <tokenizer> /data/local/tmp/llama/")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.previousAddModelStep() }) {
                            Text("Back")
                        }
                    }
                )
            } else {
                SingleChoiceDialogWithPreselection(
                    title = "Step 2: Select Tokenizer",
                    options = viewModel.tokenizerFiles.toList(),
                    selectedOption = null,
                    onSelect = { selected ->
                        viewModel.selectTempTokenizer(selected)
                    },
                    onDismiss = { viewModel.previousAddModelStep() },
                    dismissButtonText = "Back"
                )
            }
        }
        3 -> {
            // Step 3: Select adapter PTDs (0 or more, optional)
            val appColors = LocalAppColors.current
            
            AlertDialog(
                onDismissRequest = { viewModel.previousAddModelStep() },
                title = { Text("Step 3: Select Adapter PTDs") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    ) {
                        Text(
                            text = "Select 0 or more adapter PTD files for this model (optional):",
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        if (viewModel.dataPathFiles.isEmpty()) {
                            Text(
                                text = "No .ptd files found in /data/local/tmp/llama/",
                                fontSize = 12.sp,
                                color = appColors.settingsSecondaryText
                            )
                        } else {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                viewModel.dataPathFiles.forEach { adapterPath ->
                                    val isSelected = viewModel.tempAdapterPaths.contains(adapterPath)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isSelected) {
                                                    viewModel.removeTempAdapter(adapterPath)
                                                } else {
                                                    viewModel.addTempAdapter(adapterPath)
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        androidx.compose.material3.Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null
                                        )
                                        Text(
                                            text = adapterPath.substringAfterLast('/'),
                                            modifier = Modifier.padding(start = 8.dp),
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Selected: ${viewModel.tempAdapterPaths.size} adapter(s)",
                            fontSize = 12.sp,
                            color = appColors.settingsSecondaryText
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmAddModel() }) {
                        Text("Add Model")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.previousAddModelStep() }) {
                        Text("Back")
                    }
                }
            )
        }
    }
}

@Composable
private fun SingleChoiceDialogWithPreselection(
    title: String,
    options: List<String>,
    selectedOption: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    dismissButtonText: String = "Cancel"
) {
    var currentSelection by remember { mutableStateOf(selectedOption) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentSelection = option
                                onSelect(option)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentSelection == option,
                            onClick = null
                        )
                        Text(
                            text = option.substringAfterLast('/'),
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissButtonText)
            }
        }
    )
}

@Composable
private fun RemoveModelDialog(viewModel: ModelSettingsViewModel) {
    if (!viewModel.showRemoveModelDialog) return

    val modelToRemove = viewModel.modelToRemove?.let { viewModel.moduleSettings.getModelById(it) }
    val modelName = modelToRemove?.displayName ?: "this model"

    AlertDialog(
        onDismissRequest = { viewModel.cancelRemoveModel() },
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null
            )
        },
        title = { Text("Remove Model") },
        text = { Text("Remove $modelName? Chat history will be preserved.") },
        confirmButton = {
            TextButton(onClick = { viewModel.confirmRemoveModel() }) {
                Text("Yes")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.cancelRemoveModel() }) {
                Text("No")
            }
        }
    )
}

@Composable
private fun MemoryWarningDialog(viewModel: ModelSettingsViewModel) {
    if (!viewModel.showMemoryWarningDialog) return

    val modelCount = viewModel.moduleSettings.models.size

    AlertDialog(
        onDismissRequest = { viewModel.showMemoryWarningDialog = false },
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null
            )
        },
        title = { Text("Memory Warning") },
        text = {
            Text("Loading $modelCount models simultaneously will use significant RAM. Continue?")
        },
        confirmButton = {
            TextButton(onClick = { viewModel.proceedAfterMemoryWarning() }) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.showMemoryWarningDialog = false }) {
                Text("Cancel")
            }
        }
    )
}
