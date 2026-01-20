/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.example.executorchllamademo.ui.screens.SettingsScreen
import com.example.executorchllamademo.ui.theme.LlamaDemoTheme
import com.example.executorchllamademo.viewmodel.SettingsViewModel

class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            LlamaDemoTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateBack = { finish() },
                    onLoadModel = { finish() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        viewModel.refreshFilesList()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        viewModel.saveSettings()
        super.onBackPressed()
    }
    
    companion object {
        const val TEMPERATURE_MIN_VALUE = 0.0
        const val TEMPERATURE_MAX_VALUE = 1.0
    }
}
