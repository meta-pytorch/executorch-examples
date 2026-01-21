/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a chat message in the conversation.
 *
 * Note: This is not a data class because it has mutable state (text, tokensPerSecond, totalGenerationTime)
 * and custom initialization logic based on messageType.
 */
class Message(
    text: String,
    isSent: Boolean,
    val messageType: MessageType,
    val promptID: Int
) {
    // Use @JvmName to maintain Java compatibility - Java expects getIsSent()
    @get:JvmName("getIsSent")
    val isSent: Boolean = isSent
    var text: String = if (messageType == MessageType.IMAGE) "" else text
        private set

    var imagePath: String? = if (messageType == MessageType.IMAGE) text else null
        private set

    val timestamp: Long = if (messageType != MessageType.SYSTEM) System.currentTimeMillis() else 0L

    var tokensPerSecond: Float = 0f

    var totalGenerationTime: Long = 0L

    fun appendText(text: String) {
        this.text += text
    }

    fun getFormattedTimestamp(): String {
        val formatter = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.getDefault())
        val date = Date(timestamp)
        return formatter.format(date)
    }

    companion object {
        private const val TIMESTAMP_FORMAT = "hh:mm a" // example: 2:23 PM
    }
}
