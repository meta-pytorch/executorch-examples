/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.executorchllamademo.LogsActivity
import com.example.executorchllamademo.R
import com.example.executorchllamademo.SettingsActivity
import com.example.executorchllamademo.ui.components.ChatInput
import com.example.executorchllamademo.ui.components.MessageBubble
import com.example.executorchllamademo.ui.theme.*
import com.example.executorchllamademo.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages = viewModel.messages
    val inputText by viewModel.inputText
    val isGenerating by viewModel.isGenerating
    val isThinkingMode by viewModel.isThinkingMode
    val isModelLoaded by viewModel.isModelLoaded
    val memoryUsage by viewModel.memoryUsage
    
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "LlamaDemo",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                },
                actions = {
                    if (memoryUsage.isNotEmpty()) {
                        Text(
                            text = memoryUsage,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, LogsActivity::class.java))
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_article_24),
                            contentDescription = "Logs",
                            tint = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, SettingsActivity::class.java))
                        },
                        modifier = Modifier.testTag("settings")
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_settings_24),
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavBar
                )
            )
        },
        bottomBar = {
            ChatInput(
                text = inputText,
                onTextChange = { viewModel.setInputText(it) },
                onSendClick = { viewModel.sendMessage() },
                onGalleryClick = { /* Will be handled by activity */ },
                onCameraClick = { /* Will be handled by activity */ },
                onThinkModeClick = { viewModel.toggleThinkingMode() },
                isGenerating = isGenerating,
                isThinkingMode = isThinkingMode,
                showMediaButtons = isModelLoaded && viewModel.settingsFields.value.modelType?.let {
                    it == com.example.executorchllamademo.ModelType.LLAVA_1_5 ||
                    it == com.example.executorchllamademo.ModelType.GEMMA_3
                } ?: false
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .testTag("messages_view"),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messages, key = { "${it.promptID}_${it.timestamp}_${it.isSent}" }) { message ->
                MessageBubble(message = message)
            }
        }
    }
}
