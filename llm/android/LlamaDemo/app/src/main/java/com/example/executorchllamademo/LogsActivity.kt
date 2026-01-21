/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class LogsActivity : AppCompatActivity() {

    private lateinit var logsAdapter: LogsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        if (Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.status_bar)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.nav_bar)
        }

        ViewCompat.setOnApplyWindowInsetsListener(requireViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupLogs()
        setupClearLogsButton()
    }

    override fun onResume() {
        super.onResume()
        logsAdapter.clear()
        logsAdapter.addAll(ETLogging.getInstance().getLogs())
        logsAdapter.notifyDataSetChanged()
    }

    private fun setupLogs() {
        val logsListView = requireViewById<ListView>(R.id.logsListView)
        logsAdapter = LogsAdapter(this, R.layout.logs_message)

        logsListView.adapter = logsAdapter
        logsAdapter.addAll(ETLogging.getInstance().getLogs())
        logsAdapter.notifyDataSetChanged()
    }

    private fun setupClearLogsButton() {
        val clearLogsButton = requireViewById<ImageButton>(R.id.clearLogsButton)
        clearLogsButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Logs History")
                .setMessage("Do you really want to delete logs history?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    // Clear the messageAdapter and sharedPreference
                    ETLogging.getInstance().clearLogs()
                    logsAdapter.clear()
                    logsAdapter.notifyDataSetChanged()
                }
                .setNegativeButton(android.R.string.no, null)
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ETLogging.getInstance().saveLogs()
    }
}
