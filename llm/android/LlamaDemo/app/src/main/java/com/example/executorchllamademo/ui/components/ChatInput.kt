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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    onAudioClick: () -> Unit = {},
    onRecordClick: () -> Unit = {},
    onThinkModeClick: () -> Unit,
    isGenerating: Boolean,
    isThinkingMode: Boolean,
    showMediaButtons: Boolean,
    isVisionModel: Boolean = false,
    isAudioModel: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isSendEnabled = text.isNotBlank() || isGenerating
    var isExpanded by remember { mutableStateOf(false) }

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
                contentDescription = stringResource(R.string.think_mode),
                tint = if (isThinkingMode) Primary else TextSecondary
            )
        }
        
        // Media buttons with "+" toggle
        if (showMediaButtons) {
            IconButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("addMediaButton")
            ) {
                Icon(
                    painter = painterResource(R.drawable.baseline_add_24),
                    contentDescription = stringResource(R.string.add_media_description),
                    tint = if (isExpanded) Primary else TextSecondary
                )
            }

            if (isExpanded) {
                if (isVisionModel) {
                    IconButton(
                        onClick = onGalleryClick,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("galleryButton")
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_image_48),
                            contentDescription = stringResource(R.string.gallery_description),
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
                            contentDescription = stringResource(R.string.camera_description),
                            tint = TextSecondary
                        )
                    }
                }

                if (isAudioModel) {
                    IconButton(
                        onClick = onAudioClick,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("audioButton")
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_audio_file_48),
                            contentDescription = stringResource(R.string.audio_description),
                            tint = TextSecondary
                        )
                    }

                    IconButton(
                        onClick = onRecordClick,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("recordButton")
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.outline_add_box_48), // Using this as placeholder for record
                            contentDescription = stringResource(R.string.record_description),
                            tint = TextSecondary
                        )
                    }
                }
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
                            text = stringResource(R.string.type_message_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    innerTextField()
                }
            )
        }
        
        // Send/Stop button
        val sendDesc = stringResource(R.string.send_description)
        val stopDesc = stringResource(R.string.stop_description)
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
                .semantics { 
                    contentDescription = if (isGenerating) stopDesc else sendDesc 
                }
        ) {
            Icon(
                painter = painterResource(
                    if (isGenerating) R.drawable.baseline_stop_24 else R.drawable.baseline_send_24
                ),
                contentDescription = null,
                tint = TextOnPrimary
            )
        }
    }
}
