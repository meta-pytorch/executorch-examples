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

data class Message(
    val isSent: Boolean,
    val messageType: MessageType,
    val promptID: Int,
    private var _text: String = "",
    private var _imagePath: String = "",
    var tokensPerSecond: Float = 0f,
    var totalGenerationTime: Long = 0L,
    val timestamp: Long = if (messageType != MessageType.SYSTEM) System.currentTimeMillis() else 0L,
    val id: String = java.util.UUID.randomUUID().toString()
) {
    var text: String
        get() = _text
        set(value) { _text = value }
    
    val imagePath: String
        get() = _imagePath

    fun appendText(additionalText: String) {
        _text += additionalText
    }

    fun getFormattedTimestamp(): String {
        val formatter = SimpleDateFormat(TIMESTAMP_FORMAT, Locale.getDefault())
        val date = Date(timestamp)
        return formatter.format(date)
    }

    companion object {
        private const val TIMESTAMP_FORMAT = "hh:mm a" // example: 2:23 PM

        /**
         * Factory method for creating a text message
         */
        fun textMessage(text: String, isSent: Boolean, promptID: Int): Message {
            return Message(
                isSent = isSent,
                messageType = MessageType.TEXT,
                promptID = promptID,
                _text = text
            )
        }

        /**
         * Factory method for creating an image message
         */
        fun imageMessage(imagePath: String, isSent: Boolean, promptID: Int): Message {
            return Message(
                isSent = isSent,
                messageType = MessageType.IMAGE,
                promptID = promptID,
                _imagePath = imagePath
            )
        }

        /**
         * Factory method for creating a system message
         */
        fun systemMessage(text: String, promptID: Int): Message {
            return Message(
                isSent = false,
                messageType = MessageType.SYSTEM,
                promptID = promptID,
                _text = text
            )
        }
    }
}
