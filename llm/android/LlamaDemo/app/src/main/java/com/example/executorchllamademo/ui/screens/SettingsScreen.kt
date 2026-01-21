/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.executorchllamademo.BackendType
import com.example.executorchllamademo.ModelType
import com.example.executorchllamademo.PromptFormat
import com.example.executorchllamademo.R
import com.example.executorchllamademo.ui.components.SettingsRow
import com.example.executorchllamademo.ui.theme.BtnDisabled
import com.example.executorchllamademo.ui.theme.BtnEnabled
import com.example.executorchllamademo.ui.theme.NavBar
import com.example.executorchllamademo.ui.theme.TextOnPrimary
import com.example.executorchllamademo.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBackPressed: () -> Unit = {},
    onLoadModel: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavBar)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Settings",
                color = TextOnPrimary,
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
                .padding(16.dp)
        ) {
            // Backend selector
            SettingsRow(
                label = "Backend",
                value = viewModel.settingsFields.backendType.toString(),
                onClick = { viewModel.showBackendDialog = true }
            )

            // Only show these for non-MediaTek backends
            if (!viewModel.isMediaTekMode()) {
                Spacer(modifier = Modifier.height(12.dp))

                // Model selector
                SettingsRow(
                    label = "Model",
                    value = viewModel.getFilenameFromPath(viewModel.settingsFields.modelFilePath)
                        .ifEmpty { "no model selected" },
                    onClick = {
                        viewModel.refreshFileLists()
                        viewModel.showModelDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Tokenizer selector
                SettingsRow(
                    label = "Tokenizer",
                    value = viewModel.getFilenameFromPath(viewModel.settingsFields.tokenizerFilePath)
                        .ifEmpty { "no tokenizer selected" },
                    onClick = {
                        viewModel.refreshFileLists()
                        viewModel.showTokenizerDialog = true
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Data path selector
                SettingsRow(
                    label = "Data Path",
                    value = viewModel.getFilenameFromPath(viewModel.settingsFields.dataPath)
                        .ifEmpty { "no data path selected" },
                    onClick = {
                        viewModel.refreshFileLists()
                        viewModel.showDataPathDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Model type selector
            SettingsRow(
                label = "Model Type",
                value = viewModel.settingsFields.modelType.toString(),
                onClick = { viewModel.showModelTypeDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

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

            if (!viewModel.isMediaTekMode()) {
                Spacer(modifier = Modifier.height(24.dp))

                // Temperature
                Text(
                    text = "Parameters",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(8.dp))

                var temperatureText by remember(viewModel.settingsFields.temperature) {
                    mutableStateOf(viewModel.settingsFields.temperature.toString())
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Temperature",
                        fontSize = 14.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(0.4f)
                    )

                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = temperatureText,
                            onValueChange = { newValue ->
                                temperatureText = newValue
                                newValue.toDoubleOrNull()?.let {
                                    viewModel.updateTemperature(it)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            textStyle = TextStyle(fontSize = 14.sp, color = Color.Black)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // System Prompt
            PromptSection(
                title = "System Prompt",
                value = viewModel.settingsFields.systemPrompt,
                onValueChange = { viewModel.updateSystemPrompt(it) },
                onReset = { viewModel.showResetSystemPromptDialog = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // User Prompt Format
            PromptSection(
                title = "Prompt Format",
                value = viewModel.settingsFields.userPrompt,
                onValueChange = { viewModel.updateUserPrompt(it) },
                onReset = { viewModel.showResetUserPromptDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Clear Chat button
            Button(
                onClick = { viewModel.showClearChatDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BtnEnabled),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Clear Chat History", color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Dialogs
    BackendDialog(viewModel)
    ModelDialog(viewModel)
    TokenizerDialog(viewModel)
    DataPathDialog(viewModel)
    ModelTypeDialog(viewModel)
    LoadModelDialog(viewModel, onLoadModel, onBackPressed)
    ClearChatDialog(viewModel)
    ResetSystemPromptDialog(viewModel)
    ResetUserPromptDialog(viewModel)
    InvalidPromptDialog(viewModel)
}

@Composable
private fun PromptSection(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onReset) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_autorenew_24),
                contentDescription = "Reset $title",
                tint = Color.Black
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(fontSize = 14.sp, color = Color.Black)
        )
    }
}

@Composable
private fun BackendDialog(viewModel: SettingsViewModel) {
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
private fun ModelDialog(viewModel: SettingsViewModel) {
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
private fun TokenizerDialog(viewModel: SettingsViewModel) {
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
private fun DataPathDialog(viewModel: SettingsViewModel) {
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
private fun ModelTypeDialog(viewModel: SettingsViewModel) {
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
    viewModel: SettingsViewModel,
    onLoadModel: () -> Unit,
    onBackPressed: () -> Unit
) {
    if (viewModel.showLoadModelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showLoadModelDialog = false },
            icon = {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_dialog_alert),
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
private fun ClearChatDialog(viewModel: SettingsViewModel) {
    if (viewModel.showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearChatDialog = false },
            icon = {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_dialog_alert),
                    contentDescription = null
                )
            },
            title = { Text("Delete Chat History") },
            text = { Text("Do you really want to delete chat history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmClearChat()
                        viewModel.showClearChatDialog = false
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearChatDialog = false }) {
                    Text("No")
                }
            }
        )
    }
}

@Composable
private fun ResetSystemPromptDialog(viewModel: SettingsViewModel) {
    if (viewModel.showResetSystemPromptDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showResetSystemPromptDialog = false },
            icon = {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_dialog_alert),
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
private fun ResetUserPromptDialog(viewModel: SettingsViewModel) {
    if (viewModel.showResetUserPromptDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showResetUserPromptDialog = false },
            icon = {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_dialog_alert),
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
private fun InvalidPromptDialog(viewModel: SettingsViewModel) {
    if (viewModel.showInvalidPromptDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showInvalidPromptDialog = false },
            icon = {
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_dialog_alert),
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
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == option,
                            onClick = {
                                selectedOption = option
                                onSelect(option)
                            }
                        )
                        Text(
                            text = option.substringAfterLast('/'),
                            modifier = Modifier.padding(start = 8.dp),
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
