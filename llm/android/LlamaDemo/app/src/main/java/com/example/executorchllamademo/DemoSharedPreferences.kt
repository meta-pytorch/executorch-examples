/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DemoSharedPreferences(private val context: Context) {

    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        context.getString(R.string.demo_pref_file_key),
        Context.MODE_PRIVATE
    )

    fun getSavedMessages(): String {
        return sharedPreferences.getString(
            context.getString(R.string.saved_messages_json_key),
            ""
        ) ?: ""
    }

    fun addMessages(messageAdapter: MessageAdapter) {
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val msgJSON = gson.toJson(messageAdapter.savedMessages)
        editor.putString(context.getString(R.string.saved_messages_json_key), msgJSON)
        editor.apply()
    }

    fun removeExistingMessages() {
        val editor = sharedPreferences.edit()
        editor.remove(context.getString(R.string.saved_messages_json_key))
        editor.apply()
    }

    fun addSettings(settingsFields: SettingsFields) {
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val settingsJSON = gson.toJson(settingsFields)
        editor.putString(context.getString(R.string.settings_json_key), settingsJSON)
        editor.apply()
    }

    fun getSettings(): String {
        return sharedPreferences.getString(
            context.getString(R.string.settings_json_key),
            ""
        ) ?: ""
    }

    fun saveLogs() {
        val editor = sharedPreferences.edit()
        val gson = Gson()
        // Create a copy to avoid ConcurrentModificationException if logs are added during serialization
        val logsCopy = ArrayList(ETLogging.getInstance().getLogs())
        val msgJSON = gson.toJson(logsCopy)
        editor.putString(context.getString(R.string.logs_json_key), msgJSON)
        editor.apply()
    }

    fun removeExistingLogs() {
        val editor = sharedPreferences.edit()
        editor.remove(context.getString(R.string.logs_json_key))
        editor.apply()
    }

    fun getSavedLogs(): ArrayList<AppLog> {
        val logsJSONString = sharedPreferences.getString(
            context.getString(R.string.logs_json_key),
            null
        )
        if (logsJSONString.isNullOrEmpty()) {
            return ArrayList()
        }
        val gson = Gson()
        val type = object : TypeToken<ArrayList<AppLog>>() {}.type
        return gson.fromJson(logsJSONString, type) ?: ArrayList()
    }
}
