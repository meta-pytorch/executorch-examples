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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.AppSettings
import com.example.executorchllamademo.AppearanceMode
import com.example.executorchllamademo.DemoSharedPreferences
import com.example.executorchllamademo.ModuleSettings
import com.example.executorchllamademo.ui.components.SettingsRow
import com.example.executorchllamademo.ui.theme.BtnEnabled
import com.example.executorchllamademo.ui.theme.LocalAppColors

@Composable
fun AppSettingsScreen(
    onBackPressed: () -> Unit = {},
    onAppearanceChanged: (AppearanceMode) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val appColors = LocalAppColors.current

    var appSettings by remember { mutableStateOf(AppSettings()) }
    var moduleSettings by remember { mutableStateOf(ModuleSettings()) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var maxSeqLenText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val prefs = DemoSharedPreferences(context)
        appSettings = prefs.getAppSettings()
        moduleSettings = prefs.getModuleSettings()
        maxSeqLenText = appSettings.maxSeqLen.toString()
    }

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
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = appColors.textOnNavBar
                )
            }
            Text(
                text = "App Settings",
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
            // Appearance section header
            Text(
                text = "Appearance",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.settingsText
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Appearance selector
            SettingsRow(
                label = "Theme",
                value = appSettings.appearanceMode.displayName,
                onClick = { showAppearanceDialog = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Model Configuration section header
            Text(
                text = "Model Configuration",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.settingsText
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Max Seq Len input field
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.settingsRowBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Max Sequence Length",
                    fontSize = 14.sp,
                    color = appColors.settingsText,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = maxSeqLenText,
                    onValueChange = { newValue ->
                        maxSeqLenText = newValue
                        val newMaxSeqLen = newValue.toIntOrNull()
                        if (newMaxSeqLen != null && newMaxSeqLen > 0) {
                            appSettings = appSettings.copy(maxSeqLen = newMaxSeqLen)
                            val prefs = DemoSharedPreferences(context)
                            prefs.saveAppSettings(appSettings)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = appColors.settingsText,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(appColors.settingsText),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, appColors.settingsText.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 12.dp, vertical = 14.dp)
                        ) {
                            if (maxSeqLenText.isEmpty()) {
                                Text(
                                    text = "Enter max sequence length",
                                    color = appColors.settingsText.copy(alpha = 0.5f),
                                    fontSize = 16.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                Text(
                    text = "Maximum number of tokens to generate (default: ${AppSettings.DEFAULT_MAX_SEQ_LEN})",
                    fontSize = 12.sp,
                    color = appColors.settingsText.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Conversation section header
            Text(
                text = "Conversation",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.settingsText
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Save Chat History toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.settingsRowBackground, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Save Chat History",
                        fontSize = 14.sp,
                        color = appColors.settingsText
                    )
                    Text(
                        text = "Persist conversations between sessions",
                        fontSize = 12.sp,
                        color = appColors.settingsText.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = appSettings.saveChatHistory,
                    onCheckedChange = { enabled ->
                        appSettings = appSettings.copy(saveChatHistory = enabled)
                        val prefs = DemoSharedPreferences(context)
                        prefs.saveAppSettings(appSettings)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BtnEnabled
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Clear Chat button
            Button(
                onClick = { showClearChatDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BtnEnabled),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Clear Conversation History", color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Appearance Dialog
    if (showAppearanceDialog) {
        AppearanceSelectionDialog(
            currentMode = appSettings.appearanceMode,
            onSelect = { mode ->
                appSettings = appSettings.copy(appearanceMode = mode)
                val prefs = DemoSharedPreferences(context)
                prefs.saveAppSettings(appSettings)
                onAppearanceChanged(mode)
                showAppearanceDialog = false
            },
            onDismiss = { showAppearanceDialog = false }
        )
    }

    // Clear Chat Confirmation Dialog
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = null
                )
            },
            title = { Text("Clear Conversation") },
            text = { Text("Are you sure you want to clear all conversation history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val prefs = DemoSharedPreferences(context)
                        moduleSettings = moduleSettings.copy(isClearChatHistory = true)
                        prefs.saveModuleSettings(moduleSettings)
                        // Also clear the saved messages immediately
                        prefs.removeExistingMessages()
                        showClearChatDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AppearanceSelectionDialog(
    currentMode: AppearanceMode,
    onSelect: (AppearanceMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableStateOf<AppearanceMode?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                AppearanceMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedOption = mode
                                onSelect(mode)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == mode || (selectedOption == null && currentMode == mode),
                            onClick = null
                        )
                        Text(
                            text = mode.displayName,
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
