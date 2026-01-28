/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.ModelInfo
import com.example.executorchllamademo.ui.theme.LocalAppColors
import com.example.executorchllamademo.ui.viewmodel.ConfigLoadState
import com.example.executorchllamademo.ui.viewmodel.ModelDownloadState

private const val DEFAULT_CONFIG_URL = "https://raw.githubusercontent.com/meta-pytorch/executorch-examples/889ccc6e88813cbf03775889beed29b793d0c8db/llm/android/LlamaDemo/app/src/main/assets/preset_models.json"

@Composable
fun SelectPresetModelScreen(
    availableModels: Map<String, ModelInfo>,
    modelStates: Map<String, ModelDownloadState>,
    configLoadState: ConfigLoadState,
    onBackPressed: () -> Unit,
    onDownloadClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onModelClick: (String) -> Unit,
    onLoadConfigFromUrl: (String) -> Unit,
    onResetConfig: () -> Unit
) {
    val appColors = LocalAppColors.current
    val scrollState = rememberScrollState()
    var configUrlInput by remember { mutableStateOf(configLoadState.customUrl ?: "") }
    var resetClickCount by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.settingsBackground)
    ) {
        // Top banner with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.navBar)
                .padding(horizontal = 8.dp, vertical = 12.dp),
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
                text = "Download Preset Model",
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Config URL section
            ConfigUrlSection(
                configUrl = configUrlInput,
                onConfigUrlChange = { configUrlInput = it },
                configLoadState = configLoadState,
                onLoadClick = { onLoadConfigFromUrl(configUrlInput) },
                onResetClick = {
                    resetClickCount++
                    if (resetClickCount >= 7) {
                        // Easter egg: fill in the secret URL after 7 clicks
                        configUrlInput = DEFAULT_CONFIG_URL
                        resetClickCount = 0
                    } else {
                        configUrlInput = ""
                        onResetConfig()
                    }
                },
                placeholderUrl = DEFAULT_CONFIG_URL
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (availableModels.isEmpty()) {
                Text(
                    text = "No preset models available. Stay tuned!",
                    fontSize = 14.sp,
                    color = appColors.settingsSecondaryText
                )
            } else {
                Text(
                    text = "Select a model to download and use",
                    fontSize = 14.sp,
                    color = appColors.settingsSecondaryText
                )

                Spacer(modifier = Modifier.height(8.dp))

                availableModels.forEach { (key, modelInfo) ->
                    val state = modelStates[key] ?: ModelDownloadState()
                    val isReady = state.isModelDownloaded && state.isTokenizerDownloaded

                    PresetModelCard(
                        modelInfo = modelInfo,
                        state = state,
                        isReady = isReady,
                        onDownloadClick = { onDownloadClick(key) },
                        onDeleteClick = { onDeleteClick(key) },
                        onCardClick = { if (isReady) onModelClick(key) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigUrlSection(
    configUrl: String,
    onConfigUrlChange: (String) -> Unit,
    configLoadState: ConfigLoadState,
    onLoadClick: () -> Unit,
    onResetClick: () -> Unit,
    placeholderUrl: String
) {
    val appColors = LocalAppColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = appColors.settingsRowBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Custom Config URL",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.settingsText
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Load a custom preset configuration from a URL",
                fontSize = 12.sp,
                color = appColors.settingsSecondaryText
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = configUrl,
                onValueChange = onConfigUrlChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("config_url_field"),
                singleLine = true,
                enabled = !configLoadState.isLoading,
                placeholder = {
                    Text(
                        text = placeholderUrl,
                        fontSize = 12.sp,
                        color = appColors.settingsSecondaryText,
                        maxLines = 1
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onLoadClick,
                    enabled = configUrl.isNotBlank() && !configLoadState.isLoading,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = appColors.navBar
                    )
                ) {
                    if (configLoadState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading...")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Load")
                    }
                }

                OutlinedButton(
                    onClick = onResetClick,
                    enabled = !configLoadState.isLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Use Default")
                }
            }

            // Show current custom URL if loaded
            configLoadState.customUrl?.let { url ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Using custom config: $url",
                    fontSize = 11.sp,
                    color = Color(0xFF4CAF50)
                )
            }

            // Show error if any
            configLoadState.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
private fun PresetModelCard(
    modelInfo: ModelInfo,
    state: ModelDownloadState,
    isReady: Boolean,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCardClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isReady) {
                    Modifier.clickable(onClick = onCardClick)
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = appColors.settingsRowBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = modelInfo.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.settingsText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = getStatusText(state),
                        fontSize = 12.sp,
                        color = if (isReady) Color(0xFF4CAF50) else appColors.settingsSecondaryText
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (state.isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                } else if (isReady) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Ready",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = Color.Red,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onDownloadClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = appColors.navBar
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }
                }
            }

            // Show progress bar when downloading
            if (state.isDownloading && state.downloadProgress > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = state.downloadProgress,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Show error if any
            state.downloadError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    fontSize = 12.sp,
                    color = Color.Red
                )
            }

            // Show hint for ready models
            if (isReady) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to load model and start chat",
                    fontSize = 12.sp,
                    color = appColors.settingsSecondaryText
                )
            }
        }
    }
}

private fun getStatusText(state: ModelDownloadState): String {
    return when {
        state.isDownloading -> "Downloading..."
        state.isModelDownloaded && state.isTokenizerDownloaded -> "Ready to use"
        state.isModelDownloaded -> "Model downloaded, tokenizer missing"
        state.isTokenizerDownloaded -> "Tokenizer downloaded, model missing"
        else -> "Not downloaded"
    }
}
