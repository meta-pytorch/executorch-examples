/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.executorchllamademo.Message
import com.example.executorchllamademo.MessageType
import com.example.executorchllamademo.ui.theme.MessageBubbleReceived
import com.example.executorchllamademo.ui.theme.MessageBubbleSent
import com.example.executorchllamademo.ui.theme.MessageBubbleSystem
import com.example.executorchllamademo.ui.theme.TextOnPrimary

@Composable
fun MessageItem(
    message: Message,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val maxBubbleWidth = screenWidth * 0.75f

    when (message.messageType) {
        MessageType.SYSTEM -> SystemMessage(message, maxBubbleWidth, modifier)
        MessageType.IMAGE -> ImageMessage(message, maxBubbleWidth, modifier)
        MessageType.TEXT -> TextMessage(message, maxBubbleWidth, modifier)
    }
}

@Composable
private fun SystemMessage(
    message: Message,
    maxWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .background(
                    color = MessageBubbleSystem,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.text,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun ImageMessage(
    message: Message,
    maxWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val isSent = message.isSent
    val arrangement = if (isSent) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isSent) MessageBubbleSent else MessageBubbleReceived

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = arrangement
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .background(
                    color = bubbleColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(4.dp)
        ) {
            AsyncImage(
                model = Uri.parse(message.imagePath),
                contentDescription = "Attached image",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.FillWidth
            )
        }
    }
}

@Composable
private fun TextMessage(
    message: Message,
    maxWidth: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val isSent = message.isSent
    val arrangement = if (isSent) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isSent) MessageBubbleSent else MessageBubbleReceived
    val textColor = if (isSent) TextOnPrimary else Color.Black

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = arrangement
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .background(
                    color = bubbleColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.text,
                fontSize = 14.sp,
                color = textColor
            )

            // Show metrics if available
            if (message.tokensPerSecond > 0 || message.totalGenerationTime > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.tokensPerSecond > 0) {
                        Text(
                            text = String.format("%.2f t/s", message.tokensPerSecond),
                            fontSize = 10.sp,
                            color = if (isSent) TextOnPrimary.copy(alpha = 0.7f) else Color.Gray
                        )
                    }
                    if (message.tokensPerSecond > 0 && message.totalGenerationTime > 0) {
                        Text(
                            text = " | ",
                            fontSize = 10.sp,
                            color = if (isSent) TextOnPrimary.copy(alpha = 0.7f) else Color.Gray
                        )
                    }
                    if (message.totalGenerationTime > 0) {
                        Text(
                            text = "${message.totalGenerationTime / 1000f}s",
                            fontSize = 10.sp,
                            color = if (isSent) TextOnPrimary.copy(alpha = 0.7f) else Color.Gray
                        )
                    }
                }
            }

            // Show timestamp
            if (message.timestamp > 0) {
                Text(
                    text = message.getFormattedTimestamp(),
                    fontSize = 10.sp,
                    color = if (isSent) TextOnPrimary.copy(alpha = 0.7f) else Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
