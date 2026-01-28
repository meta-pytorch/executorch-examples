/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.executorchllamademo.ui.screens.SelectPresetModelScreen
import com.example.executorchllamademo.ui.theme.LlamaDemoTheme
import com.example.executorchllamademo.ui.viewmodel.SelectPresetModelViewModel

class SelectPresetModelActivity : ComponentActivity() {

    private var appearanceMode by mutableStateOf(AppearanceMode.SYSTEM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 21) {
            window.statusBarColor = ContextCompat.getColor(this, R.color.status_bar)
            window.navigationBarColor = ContextCompat.getColor(this, R.color.nav_bar)
        }

        loadAppearanceMode()

        setContent {
            val isDarkTheme = when (appearanceMode) {
                AppearanceMode.LIGHT -> false
                AppearanceMode.DARK -> true
                AppearanceMode.SYSTEM -> isSystemInDarkTheme()
            }

            LlamaDemoTheme(darkTheme = isDarkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: SelectPresetModelViewModel = viewModel()

                    LaunchedEffect(Unit) {
                        viewModel.initialize(this@SelectPresetModelActivity)
                    }

                    SelectPresetModelScreen(
                        availableModels = viewModel.availableModels,
                        modelStates = viewModel.modelStates,
                        configLoadState = viewModel.configLoadState,
                        onBackPressed = { finish() },
                        onDownloadClick = { key ->
                            viewModel.downloadModel(key)
                        },
                        onDeleteClick = { key ->
                            viewModel.deleteModel(key)
                        },
                        onModelClick = { key ->
                            if (viewModel.loadModelAndStartChat(key)) {
                                // Navigate to MainActivity (conversation) after loading model
                                startActivity(Intent(this@SelectPresetModelActivity, MainActivity::class.java))
                                finish()
                            }
                        },
                        onLoadConfigFromUrl = { url ->
                            viewModel.loadConfigFromUrl(url)
                        },
                        onResetConfig = {
                            viewModel.resetToDefaultConfig()
                        }
                    )
                }
            }
        }
    }

    private fun loadAppearanceMode() {
        val prefs = DemoSharedPreferences(this)
        appearanceMode = prefs.getAppSettings().appearanceMode
    }

    override fun onResume() {
        super.onResume()
        loadAppearanceMode()
    }
}
