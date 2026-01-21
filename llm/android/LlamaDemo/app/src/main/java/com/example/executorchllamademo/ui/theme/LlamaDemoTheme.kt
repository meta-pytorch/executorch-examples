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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Base colors (unchanged across themes)
val ColorPrimary = Color(0xFF4294F0)
val ColorPrimaryDark = Color(0xFF3700B3)
val ColorAccent = Color(0xFF03DAC5)
val BtnEnabled = Color(0xFF007CBA)
val BtnDisabled = Color(0xFFA2A4B6)
val MessageBubbleSent = Color(0xFF007CBA)

// Theme-specific color definitions
data class AppColors(
    val navBar: Color,
    val statusBar: Color,
    val messageBubbleReceived: Color,
    val messageBubbleSystem: Color,
    val chatBackground: Color,
    val inputBackground: Color,
    val textOnNavBar: Color,
    val textOnBubble: Color,
    val textOnInput: Color,
    val settingsBackground: Color,
    val settingsRowBackground: Color,
    val settingsText: Color,
    val settingsSecondaryText: Color,
    val logsBackground: Color,
    val logsText: Color,
    val isDark: Boolean
)

// Dark theme colors (original app design)
val DarkAppColors = AppColors(
    navBar = Color(0xFF16293D),
    statusBar = Color(0xFF16293D),
    messageBubbleReceived = Color(0xFF081D2C),
    messageBubbleSystem = Color(0xFF2A3A4A),
    chatBackground = Color(0xFFDCD7D7),
    inputBackground = Color(0xFF081D2C),
    textOnNavBar = Color.White,
    textOnBubble = Color.White,
    textOnInput = Color.White,
    settingsBackground = Color(0xFF16293D),
    settingsRowBackground = Color(0xFF2A3A4A),
    settingsText = Color.White,
    settingsSecondaryText = Color(0xFFB0B0B0),
    logsBackground = Color(0xFF121212),
    logsText = Color.White,
    isDark = true
)

// Light theme colors
val LightAppColors = AppColors(
    navBar = Color(0xFF4294F0),
    statusBar = Color(0xFF4294F0),
    messageBubbleReceived = Color(0xFFE8E8E8),
    messageBubbleSystem = Color(0xFFE0E0E0),
    chatBackground = Color(0xFFF5F5F5),
    inputBackground = Color(0xFFE0E0E0),
    textOnNavBar = Color.White,
    textOnBubble = Color(0xFF1A1A1A),
    textOnInput = Color(0xFF1A1A1A),
    settingsBackground = Color(0xFFF5F5F5),
    settingsRowBackground = Color(0xFFDCD7D7),
    settingsText = Color(0xFF1A1A1A),
    settingsSecondaryText = Color(0xFF666666),
    logsBackground = Color.White,
    logsText = Color.Black,
    isDark = false
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

// Legacy color references for backward compatibility
val NavBar: Color
    @Composable get() = LocalAppColors.current.navBar

val StatusBar: Color
    @Composable get() = LocalAppColors.current.statusBar

val MessageBubbleReceived: Color
    @Composable get() = LocalAppColors.current.messageBubbleReceived

val MessageBubbleSystem: Color
    @Composable get() = LocalAppColors.current.messageBubbleSystem

val TextOnPrimary: Color
    @Composable get() = LocalAppColors.current.textOnNavBar

// Additional helper colors
val TextPrimary = Color(0xFF000000)
val TextSecondary = Color(0xFF666666)
val Background = Color(0xFFDCD7D7)
val Surface = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = ColorPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4294F0),
    onPrimaryContainer = Color.White,
    secondary = ColorAccent,
    onSecondary = TextPrimary,
    background = Color(0xFFF5F5F5),
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = TextPrimary
)

private val DarkColorScheme = darkColorScheme(
    primary = ColorPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF16293D),
    onPrimaryContainer = Color.White,
    secondary = ColorAccent,
    onSecondary = TextPrimary,
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White
)

@Composable
fun LlamaDemoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors = if (darkTheme) DarkAppColors else LightAppColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
