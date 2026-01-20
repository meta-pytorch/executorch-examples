/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

class MessageAdapter(
    context: Context,
    resource: Int,
    val savedMessages: ArrayList<Message>
) : ArrayAdapter<Message>(context, resource) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val currentMessage = getItem(position) ?: return convertView ?: View(context)

        val layoutIdForListItem = when {
            currentMessage.messageType == MessageType.SYSTEM -> R.layout.system_message
            currentMessage.isSent -> R.layout.sent_message
            else -> R.layout.received_message
        }

        val listItemView = LayoutInflater.from(context).inflate(layoutIdForListItem, parent, false)

        if (currentMessage.messageType == MessageType.IMAGE) {
            val messageImageView = listItemView.requireViewById<ImageView>(R.id.message_image)
            messageImageView.setImageURI(Uri.parse(currentMessage.imagePath))
            val messageTextView = listItemView.requireViewById<TextView>(R.id.message_text)
            messageTextView.visibility = View.GONE
        } else {
            val messageTextView = listItemView.requireViewById<TextView>(R.id.message_text)
            messageTextView.text = currentMessage.text
        }

        var metrics = ""
        if (currentMessage.tokensPerSecond > 0) {
            metrics = String.format("%.2f", currentMessage.tokensPerSecond) + "t/s  "
        }

        if (currentMessage.totalGenerationTime > 0) {
            metrics = metrics + currentMessage.totalGenerationTime.toFloat() / 1000 + "s  "
        }

        if (currentMessage.tokensPerSecond > 0 || currentMessage.totalGenerationTime > 0) {
            val tokensView = listItemView.requireViewById<TextView>(R.id.generation_metrics)
            tokensView.text = metrics
            val separatorView = listItemView.requireViewById<TextView>(R.id.bar)
            separatorView.visibility = View.VISIBLE
        }

        if (currentMessage.timestamp > 0) {
            val timestampView = listItemView.requireViewById<TextView>(R.id.timestamp)
            timestampView.text = currentMessage.getFormattedTimestamp()
        }

        return listItemView
    }

    override fun add(msg: Message?) {
        super.add(msg)
        msg?.let { savedMessages.add(it) }
    }

    override fun clear() {
        super.clear()
        savedMessages.clear()
    }

    fun getRecentSavedTextMessages(numOfLatestPromptMessages: Int): ArrayList<Message> {
        val recentMessages = ArrayList<Message>()
        val lastIndex = savedMessages.size - 1
        var remainingPrompts = numOfLatestPromptMessages

        // In most cases lastIndex >= 0
        // A situation where the user clears chat history and enters prompt causes lastIndex = -1
        if (lastIndex >= 0) {
            var messageToAdd = savedMessages[lastIndex]
            var oldPromptID = messageToAdd.promptID

            for (i in 0 until savedMessages.size) {
                messageToAdd = savedMessages[lastIndex - i]
                if (messageToAdd.messageType != MessageType.SYSTEM) {
                    if (messageToAdd.promptID != oldPromptID) {
                        remainingPrompts--
                        oldPromptID = messageToAdd.promptID
                    }
                    if (remainingPrompts > 0) {
                        if (messageToAdd.messageType == MessageType.TEXT) {
                            recentMessages.add(messageToAdd)
                        }
                    } else {
                        break
                    }
                }
            }
            // To place the order in [input1, output1, input2, output2...]
            recentMessages.reverse()
        }

        return recentMessages
    }

    fun getMaxPromptID(): Int {
        return savedMessages.maxOfOrNull { it.promptID } ?: -1
    }

    /**
     * Checks if the last message is a duplicate system message with the given text.
     *
     * @param text The text to check against the last message
     * @return true if the last message is a system message with matching text, false otherwise
     */
    fun isDuplicateSystemMessage(text: String): Boolean {
        if (count == 0) {
            return false
        }
        val lastMessage = getItem(count - 1)
        return lastMessage != null &&
                lastMessage.messageType == MessageType.SYSTEM &&
                text == lastMessage.text
    }
}
