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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.executorchllamademo.Message
import com.example.executorchllamademo.MessageType
import com.example.executorchllamademo.ui.theme.LocalAppColors
import com.example.executorchllamademo.ui.theme.MessageBubbleSent
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText

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
    val appColors = LocalAppColors.current

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
                    color = appColors.messageBubbleSystem,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.text,
                fontSize = 12.sp,
                fontStyle = FontStyle.Italic,
                color = if (appColors.isDark) Color.LightGray else Color.Gray,
                lineHeight = 14.sp
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
    val appColors = LocalAppColors.current
    val isSent = message.isSent
    val arrangement = if (isSent) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isSent) MessageBubbleSent else appColors.messageBubbleReceived

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
    val appColors = LocalAppColors.current
    val isSent = message.isSent
    val arrangement = if (isSent) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isSent) MessageBubbleSent else appColors.messageBubbleReceived
    val textColor = if (isSent) Color.White else appColors.textOnBubble

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
            if (isSent) {
                // User messages: plain text
                Text(
                    text = message.text,
                    fontSize = 16.sp,
                    letterSpacing = 0.sp,
                    color = textColor
                )
            } else {
                // Thinking block (collapsible, shown before response)
                if (message.thinkingContent.isNotEmpty()) {
                    ThinkingBlock(
                        content = message.thinkingContent,
                        textColor = textColor
                    )
                }
                // Model responses: Markdown rendering
                if (message.text.isNotEmpty()) {
                    RichText(
                        style = RichTextStyle(
                            codeBlockStyle = CodeBlockStyle(
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = textColor
                                )
                            )
                        )
                    ) {
                        Markdown(content = message.text)
                    }
                }
            }

            // Show metrics and timestamp on the same row
            val hasMetrics = message.tokensPerSecond > 0 || message.totalGenerationTime > 0
            val hasTimestamp = message.timestamp > 0

            if (hasMetrics || hasTimestamp) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.tokensPerSecond > 0) {
                        Text(
                            text = String.format("%.2f t/s", message.tokensPerSecond),
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                    if (message.tokensPerSecond > 0 && message.totalGenerationTime > 0) {
                        Text(
                            text = " | ",
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                    if (message.totalGenerationTime > 0) {
                        Text(
                            text = "${message.totalGenerationTime / 1000f}s",
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                    if (hasMetrics && hasTimestamp) {
                        Text(
                            text = " | ",
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                    if (hasTimestamp) {
                        Text(
                            text = message.getFormattedTimestamp(),
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThinkingBlock(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "thinking_expand"
    )

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        // Header: clickable to expand/collapse
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = textColor.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotationAngle)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Thinking",
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                color = textColor.copy(alpha = 0.5f)
            )
        }

        // Collapsible thinking content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                // Left vertical line decoration
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(textColor.copy(alpha = 0.15f))
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Thinking content with Markdown rendering
                RichText(
                    modifier = Modifier.weight(1f),
                    style = RichTextStyle(
                        codeBlockStyle = CodeBlockStyle(
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = textColor.copy(alpha = 0.7f)
                            )
                        )
                    )
                ) {
                    Markdown(content = content)
                }
            }
        }
    }
}
