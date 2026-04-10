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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.executorchllamademo.ui.components.ChatInput
import com.example.executorchllamademo.ui.components.MessageItem
import com.example.executorchllamademo.ui.theme.LocalAppColors
import com.example.executorchllamademo.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBackClick: () -> Unit,
    onLogsClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    audioFiles: List<String>,
    onAudioFileSelected: (String) -> Unit
) {
    var showAudioDialog by remember { mutableStateOf(false) }
    var showModelSwitcherDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val appColors = LocalAppColors.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    // Detect whether the user is near the bottom of the list
    val isNearBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= layoutInfo.totalItemsCount - 2
        }
    }

    // Scroll to bottom when a new message is added (user sent or new response placeholder)
    LaunchedEffect(viewModel.messages.size) {
        if (viewModel.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewModel.messages.size - 1)
        }
    }

    // During generation: poll and scroll only if user is near bottom (throttled)
    LaunchedEffect(viewModel.isGenerating) {
        if (viewModel.isGenerating) {
            while (viewModel.isGenerating) {
                delay(300)
                if (isNearBottom && viewModel.messages.isNotEmpty()) {
                    listState.scrollToItem(viewModel.messages.size - 1)
                }
            }
            // Final animated scroll when generation completes
            if (isNearBottom && viewModel.messages.isNotEmpty()) {
                listState.animateScrollToItem(viewModel.messages.size - 1)
            }
        }
    }

    // Periodically update memory usage
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateMemoryUsage()
            delay(1000)
        }
    }

    // Check settings on resume (including when navigating back from Settings)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAndLoadSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Save messages when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chat with assistant",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = appColors.textOnNavBar
                        )
                    }
                },
                actions = {
                    Text(
                        text = viewModel.ramUsage,
                        color = appColors.textOnNavBar,
                        fontSize = 14.sp
                    )
                    // Model switcher button - only visible in LoRA mode
                    if (viewModel.isLoraMode) {
                        IconButton(onClick = { showModelSwitcherDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.SwapHoriz,
                                contentDescription = "Switch Model",
                                tint = appColors.textOnNavBar
                            )
                        }
                    }
                    IconButton(onClick = onLogsClick) {
                        Icon(
                            imageVector = Icons.Filled.Article,
                            contentDescription = "Logs",
                            tint = appColors.textOnNavBar
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = appColors.navBar,
                    titleContentColor = appColors.textOnNavBar
                )
            )
        },
        containerColor = appColors.chatBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            // Messages list with scroll-to-bottom FAB overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(appColors.chatBackground)
                        .padding(horizontal = 8.dp)
                ) {
                    items(
                        items = viewModel.messages,
                        key = { message -> message.id }
                    ) { message ->
                        MessageItem(message = message)
                    }
                }

                // Scroll-to-bottom FAB: shown when user scrolls up
                if (!isNearBottom && viewModel.messages.isNotEmpty()) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(viewModel.messages.size - 1)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(36.dp),
                        containerColor = appColors.navBar
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            tint = appColors.textOnNavBar,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Chat input
            ChatInput(
                inputText = viewModel.inputText,
                onInputTextChange = { viewModel.inputText = it },
                isModelReady = viewModel.isModelReady,
                isGenerating = viewModel.isGenerating,
                onSendClick = { viewModel.sendMessage() },
                onStopClick = { viewModel.stopGeneration() },
                showMediaButtons = viewModel.showMediaButtons,
                showMediaSelector = viewModel.showMediaSelector,
                onAddMediaClick = { viewModel.toggleMediaSelector() },
                onGalleryClick = onGalleryClick,
                onCameraClick = onCameraClick,
                onAudioClick = { showAudioDialog = true },
                selectedImages = viewModel.selectedImages,
                onRemoveImage = { viewModel.removeImage(it) },
                onAddMoreImages = onGalleryClick,
                supportsImageInput = viewModel.supportsImageInput,
                supportsAudioInput = viewModel.supportsAudioInput
            )
        }
    }

    // Model load error dialog
    if (viewModel.showModelLoadErrorDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissModelLoadErrorDialog() },
            title = { Text("Model Load failure") },
            text = { Text(viewModel.modelLoadError) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissModelLoadErrorDialog() }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    // Audio file selection dialog
    if (showAudioDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("Select audio feature path") },
            text = {
                Column {
                    audioFiles.forEach { audioFile ->
                        Text(
                            text = audioFile,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onAudioFileSelected(audioFile)
                                    showAudioDialog = false
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAudioDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Model switcher dialog (LoRA mode)
    if (showModelSwitcherDialog && viewModel.isLoraMode) {
        AlertDialog(
            onDismissRequest = { showModelSwitcherDialog = false },
            title = { Text("Switch Model") },
            text = {
                Column {
                    if (viewModel.availableModels.isEmpty()) {
                        Text("No models configured. Add models in Settings.")
                    } else {
                        viewModel.availableModels.forEach { model ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.switchToModel(model.id)
                                        showModelSwitcherDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = model.id == viewModel.activeModelId,
                                    onClick = {
                                        viewModel.switchToModel(model.id)
                                        showModelSwitcherDialog = false
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = model.displayName.ifEmpty { "Unknown" },
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${model.modelType}",
                                        fontSize = 12.sp,
                                        color = appColors.settingsSecondaryText
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModelSwitcherDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
