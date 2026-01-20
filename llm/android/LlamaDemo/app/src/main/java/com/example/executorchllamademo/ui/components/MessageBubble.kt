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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.executorchllamademo.Message
import com.example.executorchllamademo.MessageType
import com.example.executorchllamademo.ui.theme.*

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    when (message.messageType) {
        MessageType.SYSTEM -> SystemMessageBubble(message, modifier)
        else -> ChatMessageBubble(message, modifier)
    }
}

@Composable
private fun ChatMessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (message.isSent) SentMessageBackground else ReceivedMessageBackground
    val alignment = if (message.isSent) Alignment.End else Alignment.Start
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isSent) 16.dp else 4.dp,
                        bottomEnd = if (message.isSent) 4.dp else 16.dp
                    )
                )
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Column {
                if (message.messageType == MessageType.IMAGE && message.imagePath.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(message.imagePath)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Image message",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (message.text.isNotEmpty()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                }
            }
        }
        
        // Metrics row (timestamp, tokens/second, generation time)
        Row(
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (message.timestamp > 0) {
                Text(
                    text = message.getFormattedTimestamp(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            
            if (message.tokensPerSecond > 0 || message.totalGenerationTime > 0) {
                Text(
                    text = "|",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            
            if (message.tokensPerSecond > 0) {
                Text(
                    text = String.format("%.2ft/s", message.tokensPerSecond),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            
            if (message.totalGenerationTime > 0) {
                Text(
                    text = "${message.totalGenerationTime / 1000f}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SystemMessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SystemMessageBackground.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}
