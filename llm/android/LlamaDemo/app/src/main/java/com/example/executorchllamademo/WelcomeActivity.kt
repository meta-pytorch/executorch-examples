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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.executorchllamademo.ui.screens.WelcomeScreen
import com.example.executorchllamademo.ui.theme.LlamaDemoTheme

class WelcomeActivity : ComponentActivity() {

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
                    WelcomeScreen(
                        onLoadModelClick = {
                            startActivity(Intent(this@WelcomeActivity, SettingsActivity::class.java))
                        },
                        onAppSettingsClick = {
                            startActivity(Intent(this@WelcomeActivity, AppSettingsActivity::class.java))
                        },
                        onStartChatClick = {
                            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
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
