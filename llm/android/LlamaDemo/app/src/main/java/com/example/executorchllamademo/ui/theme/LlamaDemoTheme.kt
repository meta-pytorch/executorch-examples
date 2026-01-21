/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorchllamademo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// App colors from colors.xml
val NavBar = Color(0xFF16293D)
val StatusBar = Color(0xFF16293D)
val ColorPrimary = Color(0xFF4294F0)
val ColorPrimaryDark = Color(0xFF3700B3)
val ColorAccent = Color(0xFF03DAC5)
val BtnEnabled = Color(0xFF007CBA)
val BtnDisabled = Color(0xFFA2A4B6)

// Additional colors used in layouts
val MessageBubbleSent = Color(0xFF007CBA)
val MessageBubbleReceived = Color(0xFFDCD7D7)
val MessageBubbleSystem = Color(0xFFF5F5F5)
val TextPrimary = Color(0xFF000000)
val TextSecondary = Color(0xFF666666)
val TextOnPrimary = Color(0xFFFFFFFF)
val Background = Color(0xFFFFFFFF)
val Surface = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = ColorPrimary,
    onPrimary = TextOnPrimary,
    primaryContainer = NavBar,
    onPrimaryContainer = TextOnPrimary,
    secondary = ColorAccent,
    onSecondary = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = MessageBubbleReceived,
    onSurfaceVariant = TextPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorPrimary,
    onPrimary = TextOnPrimary,
    primaryContainer = NavBar,
    onPrimaryContainer = TextOnPrimary,
    secondary = ColorAccent,
    onSecondary = TextPrimary,
    background = Color(0xFF121212),
    onBackground = TextOnPrimary,
    surface = Color(0xFF1E1E1E),
    onSurface = TextOnPrimary
)

@Composable
fun LlamaDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
