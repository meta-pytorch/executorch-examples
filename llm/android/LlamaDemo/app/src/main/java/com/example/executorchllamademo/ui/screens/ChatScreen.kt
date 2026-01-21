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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.ui.components.ChatInput
import com.example.executorchllamademo.ui.components.MessageItem
import com.example.executorchllamademo.ui.theme.LocalAppColors
import com.example.executorchllamademo.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onSettingsClick: () -> Unit,
    onLogsClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onAudioClick: (List<String>) -> Unit
) {
    val listState = rememberLazyListState()
    val appColors = LocalAppColors.current
    val focusManager = LocalFocusManager.current

    // Auto-scroll to bottom when new messages are added or content changes during generation
    LaunchedEffect(viewModel.messages.size, viewModel.scrollTrigger) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    // Periodically update memory usage
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateMemoryUsage()
            delay(1000)
        }
    }

    // Check settings on resume
    LaunchedEffect(Unit) {
        viewModel.checkAndLoadSettings()
    }

    // Save messages when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveMessages()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(appColors.chatBackground)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                // Clear focus when tapping outside the input field (dismisses keyboard)
                focusManager.clearFocus()
            }
    ) {
        // Top banner
        TopBanner(
            ramUsage = viewModel.ramUsage,
            onLogsClick = onLogsClick,
            onSettingsClick = onSettingsClick
        )

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(appColors.chatBackground)
                .padding(horizontal = 8.dp)
        ) {
            itemsIndexed(
                items = viewModel.messages,
                key = { index, message -> "${index}_${message.timestamp}_${message.promptID}" }
            ) { _, message ->
                MessageItem(message = message)
            }
        }

        // Chat input
        ChatInput(
            inputText = viewModel.inputText,
            onInputTextChange = { viewModel.inputText = it },
            isModelReady = viewModel.isModelReady,
            isGenerating = viewModel.isGenerating,
            thinkMode = viewModel.thinkMode,
            onThinkModeToggle = { viewModel.toggleThinkMode() },
            onSendClick = { viewModel.sendMessage() },
            onStopClick = { viewModel.stopGeneration() },
            showMediaButtons = viewModel.showMediaButtons,
            showMediaSelector = viewModel.showMediaSelector,
            onAddMediaClick = { viewModel.toggleMediaSelector() },
            onGalleryClick = onGalleryClick,
            onCameraClick = onCameraClick,
            onAudioClick = { onAudioClick(emptyList()) },
            selectedImages = viewModel.selectedImages,
            onRemoveImage = { viewModel.removeImage(it) },
            onAddMoreImages = onGalleryClick,
            supportsImageInput = viewModel.supportsImageInput,
            supportsAudioInput = viewModel.supportsAudioInput
        )
    }

    // Select model dialog
    if (viewModel.showSelectModelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSelectModelDialog() },
            title = { Text("Please Select a Model") },
            text = {
                Text("Please select a model and tokenizer from the settings (top right corner) to get started.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissSelectModelDialog()
                    viewModel.addSystemMessage("To get started, select your desired model and tokenizer from the top right corner")
                }) {
                    Text("OK")
                }
            }
        )
    }

    // Model load error dialog
    if (viewModel.showModelLoadErrorDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissModelLoadErrorDialog() },
            title = { Text("Model Load failure") },
            text = { Text(viewModel.modelLoadError) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissModelLoadErrorDialog() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun TopBanner(
    ramUsage: String,
    onLogsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.navBar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Chat with assistant",
            color = appColors.textOnNavBar,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = ramUsage,
            color = appColors.textOnNavBar,
            fontSize = 14.sp
        )

        IconButton(onClick = onLogsClick) {
            Icon(
                imageVector = Icons.Filled.Article,
                contentDescription = "Logs",
                tint = appColors.textOnNavBar
            )
        }

        IconButton(onClick = onSettingsClick) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = appColors.textOnNavBar
            )
        }
    }
}
