/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.executorchllamademo.ui.screens.SettingsScreen
import com.example.executorchllamademo.ui.theme.LlamaDemoTheme
import com.example.executorchllamademo.ui.viewmodel.SettingsViewModel
import java.io.File

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.status_bar)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.nav_bar)
        }

        setContent {
            LlamaDemoTheme(darkTheme = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: SettingsViewModel = viewModel()
                    SettingsScreen(
                        viewModel = viewModel,
                        onBackPressed = {
                            viewModel.saveSettings()
                            finish()
                        },
                        onLoadModel = {
                            // Settings are saved by viewModel.confirmLoadModel()
                        }
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val TEMPERATURE_MIN_VALUE = 0.0

        private fun fileHasExtension(file: String, suffix: Array<String>): Boolean {
            return suffix.any { file.endsWith(it) }
        }

        @JvmStatic
        fun listLocalFile(path: String, suffix: Array<String>): Array<String> {
            val directory = File(path)
            if (directory.exists() && directory.isDirectory) {
                val files = directory.listFiles { _, name -> fileHasExtension(name, suffix) }
                return files?.filter { it.isFile && fileHasExtension(it.name, suffix) }
                    ?.map { it.absolutePath }
                    ?.toTypedArray()
                    ?: emptyArray()
            }
            return emptyArray()
        }
    }
}
