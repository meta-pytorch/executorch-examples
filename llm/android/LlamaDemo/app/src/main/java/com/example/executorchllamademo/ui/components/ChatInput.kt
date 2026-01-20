/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.executorchllamademo.R
import com.example.executorchllamademo.ui.theme.*

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onThinkModeClick: () -> Unit,
    isGenerating: Boolean,
    isThinkingMode: Boolean,
    showMediaButtons: Boolean,
    modifier: Modifier = Modifier
) {
    val isSendEnabled = text.isNotBlank() || isGenerating
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Think mode button
        IconButton(
            onClick = onThinkModeClick,
            modifier = Modifier
                .size(40.dp)
                .testTag("thinkModeButton")
        ) {
            Icon(
                painter = painterResource(
                    if (isThinkingMode) R.drawable.blue_lightbulb_24 
                    else R.drawable.baseline_lightbulb_24
                ),
                contentDescription = "Think mode",
                tint = if (isThinkingMode) Primary else TextSecondary
            )
        }
        
        // Media buttons
        if (showMediaButtons) {
            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("galleryButton")
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_image_48),
                    contentDescription = "Gallery",
                    tint = TextSecondary
                )
            }
            
            IconButton(
                onClick = onCameraClick,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("cameraButton")
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_camera_alt_48),
                    contentDescription = "Camera",
                    tint = TextSecondary
                )
            }
        }
        
        // Text input field
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = SurfaceVariant,
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("editTextMessage"),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize
                ),
                cursorBrush = SolidColor(Primary),
                singleLine = false,
                maxLines = 4,
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            text = "Type a message",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    innerTextField()
                }
            )
        }
        
        // Send/Stop button
        IconButton(
            onClick = onSendClick,
            enabled = isSendEnabled,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (isSendEnabled) ButtonEnabled else ButtonDisabled,
                    shape = CircleShape
                )
                .testTag("sendButton")
        ) {
            Icon(
                painter = painterResource(
                    if (isGenerating) R.drawable.baseline_stop_24 else R.drawable.baseline_send_24
                ),
                contentDescription = if (isGenerating) "Stop" else "Send",
                tint = TextOnPrimary
            )
        }
    }
}
