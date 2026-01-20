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
 * Represents a log entry with timestamp.
 */
data class AppLog(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedLog(): String {
        return "[$formattedTimestamp] $message"
    }

    private val formattedTimestamp: String
        get() = formatDate(timestamp)

    companion object {
        private const val DATE_FORMAT = "yyyy-MM-dd  HH:mm:ss"

        private fun formatDate(milliseconds: Long): String {
            val formatter = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            val date = Date(milliseconds)
            return formatter.format(date)
        }
    }
}
