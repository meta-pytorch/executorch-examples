/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.app.Application
import android.util.Log

class ETLogging : Application() {

    private lateinit var logs: ArrayList<AppLog>
    private lateinit var demoSharedPreferences: DemoSharedPreferences

    override fun onCreate() {
        super.onCreate()
        instance = this
        demoSharedPreferences = DemoSharedPreferences(applicationContext)
        logs = demoSharedPreferences.getSavedLogs()
    }

    fun log(message: String) {
        val appLog = AppLog(message)
        logs.add(appLog)
        Log.d(TAG, appLog.message)
    }

    fun getLogs(): ArrayList<AppLog> = logs

    fun clearLogs() {
        logs.clear()
        demoSharedPreferences.removeExistingLogs()
    }

    fun saveLogs() {
        demoSharedPreferences.saveLogs()
    }

    companion object {
        private const val TAG = "ETLogging"

        @Volatile
        private var instance: ETLogging? = null

        @JvmStatic
        fun getInstance(): ETLogging {
            return instance ?: throw IllegalStateException("ETLogging not initialized")
        }
    }
}
