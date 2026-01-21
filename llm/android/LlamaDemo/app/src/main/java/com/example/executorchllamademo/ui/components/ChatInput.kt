/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.executorchllamademo.R
import com.example.executorchllamademo.ui.theme.AppColors
import com.example.executorchllamademo.ui.theme.BtnDisabled
import com.example.executorchllamademo.ui.theme.ColorPrimary
import com.example.executorchllamademo.ui.theme.LocalAppColors

@Composable
fun ChatInput(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isModelReady: Boolean,
    isGenerating: Boolean,
    thinkMode: Boolean,
    onThinkModeToggle: () -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    showMediaButtons: Boolean,
    showMediaSelector: Boolean,
    onAddMediaClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onAudioClick: () -> Unit,
    selectedImages: List<Uri>,
    onRemoveImage: (Uri) -> Unit,
    onAddMoreImages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    Column(modifier = modifier.fillMaxWidth()) {
        // Media preview section
        if (selectedImages.isNotEmpty()) {
            MediaPreview(
                images = selectedImages,
                onRemoveImage = onRemoveImage,
                onAddMore = onAddMoreImages
            )
        }

        // Media selector section (gallery, camera, audio buttons)
        AnimatedVisibility(
            visible = showMediaSelector,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(appColors.navBar)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MediaButton(
                    iconRes = R.drawable.outline_image_48,
                    label = "Gallery",
                    onClick = onGalleryClick,
                    appColors = appColors
                )
                MediaButton(
                    iconRes = R.drawable.outline_camera_alt_48,
                    label = "Camera",
                    onClick = onCameraClick,
                    appColors = appColors
                )
                MediaButton(
                    iconRes = R.drawable.baseline_audio_file_48,
                    label = "Audio",
                    onClick = onAudioClick,
                    appColors = appColors
                )
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(appColors.navBar)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Add media button (+ or collapse icon)
            if (showMediaButtons) {
                IconButton(
                    onClick = onAddMediaClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (showMediaSelector) R.drawable.expand_circle_down else R.drawable.baseline_add_24
                        ),
                        contentDescription = if (showMediaSelector) "Collapse media" else "Add media",
                        tint = appColors.textOnNavBar
                    )
                }
            }

            // Think mode button
            IconButton(
                onClick = onThinkModeToggle,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (thinkMode) R.drawable.blue_lightbulb_24 else R.drawable.baseline_lightbulb_24
                    ),
                    contentDescription = "Think mode",
                    tint = if (thinkMode) Color(0xFFFFD54F) else appColors.textOnNavBar
                )
            }

            // Text input
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(appColors.inputBackground, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Type a message...",
                        color = appColors.textOnInput.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        letterSpacing = 0.sp
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        letterSpacing = 0.sp,
                        color = appColors.textOnInput
                    ),
                    singleLine = false,
                    maxLines = 4
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Send/Stop button
            val canSend = isModelReady && !isGenerating && inputText.trim().isNotEmpty()
            IconButton(
                onClick = {
                    if (isGenerating) {
                        onStopClick()
                    } else if (canSend) {
                        onSendClick()
                    }
                },
                enabled = isGenerating || canSend,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(
                        id = if (isGenerating) R.drawable.baseline_stop_24 else R.drawable.baseline_send_24
                    ),
                    contentDescription = if (isGenerating) "Stop" else "Send",
                    tint = if (isGenerating || canSend) appColors.textOnNavBar else BtnDisabled
                )
            }
        }
    }
}

@Composable
private fun MediaButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    appColors: AppColors
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(ColorPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = appColors.textOnNavBar
        )
    }
}

@Composable
fun MediaPreview(
    images: List<Uri>,
    onRemoveImage: (Uri) -> Unit,
    onAddMore: () -> Unit
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appColors.navBar)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(images) { uri ->
                Box {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    // Close button
                    IconButton(
                        onClick = { onRemoveImage(uri) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_close_24),
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Add more button
        IconButton(
            onClick = onAddMore,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_add_24),
                contentDescription = "Add more images",
                tint = appColors.textOnNavBar
            )
        }

        // Close preview button
        IconButton(
            onClick = { images.forEach { onRemoveImage(it) } },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_close_24),
                contentDescription = "Close preview",
                tint = appColors.textOnNavBar
            )
        }
    }
}
