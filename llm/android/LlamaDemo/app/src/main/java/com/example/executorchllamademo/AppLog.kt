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

data class AppLog(
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getFormattedLog(): String {
        return "[${getFormattedTimeStamp()}] $message"
    }

    private fun getFormattedTimeStamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.getDefault())
        val date = Date(timestamp)
        return formatter.format(date)
    }
}
