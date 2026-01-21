/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.R
import com.example.executorchllamademo.ui.components.ChatInput
import com.example.executorchllamademo.ui.components.MessageItem
import com.example.executorchllamademo.ui.theme.NavBar
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

    // Auto-scroll to bottom when new messages are added
    LaunchedEffect(viewModel.messages.size) {
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
            .background(Color(0xFFDCD7D7))
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
                .background(Color(0xFFDCD7D7))
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
            modifier = Modifier.background(NavBar)
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NavBar)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Chat with assistant",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = ramUsage,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onLogsClick) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_article_24),
                contentDescription = "Logs",
                tint = Color.White
            )
        }

        IconButton(onClick = onSettingsClick) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_settings_24),
                contentDescription = "Settings",
                tint = Color.White
            )
        }
    }
}
