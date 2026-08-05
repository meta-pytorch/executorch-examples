/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.example.executorch.athleticintelligence.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val IgLightBackground = Color(0xFFFAFAFA)
private val IgLightSurface = Color(0xFFFFFFFF)
private val IgLightSurfaceVariant = Color(0xFFF2F2F2)
private val IgLightOutline = Color(0xFFDBDBDB)
private val IgLightOutlineVariant = Color(0xFFEFEFEF)
private val IgLightOnSurface = Color(0xFF262626)
private val IgLightOnSurfaceVariant = Color(0xFF8E8E8E)
private val IgLightTertiary = Color(0xFFC7C7C7)

private val IgDarkBackground = Color(0xFF000000)
private val IgDarkSurface = Color(0xFF121212)
private val IgDarkSurfaceVariant = Color(0xFF262626)
private val IgDarkOutline = Color(0xFF363636)
private val IgDarkOnSurface = Color(0xFFF5F5F5)
private val IgDarkOnSurfaceVariant = Color(0xFFA8A8A8)

private val IgBlueLink = Color(0xFF0095F6)
private val IgError = Color(0xFFED4956)
private val IgSuccess = Color(0xFF49B88A)
private val IgWarning = Color(0xFFE0A93F)

val MetaGradientBrush = Brush.horizontalGradient(
    listOf(
        Color(0xFFF58529),
        Color(0xFFDD2A7B),
        Color(0xFF8134AF),
        Color(0xFF515BD4),
    )
)

val LocalMetaGradient = staticCompositionLocalOf { MetaGradientBrush }

private val LightColors = lightColorScheme(
    primary = IgLightOnSurface,
    onPrimary = Color.White,
    secondary = IgBlueLink,
    onSecondary = Color.White,
    tertiary = Color(0xFFDD2A7B),
    onTertiary = Color.White,
    background = IgLightBackground,
    onBackground = IgLightOnSurface,
    surface = IgLightSurface,
    onSurface = IgLightOnSurface,
    surfaceVariant = IgLightSurfaceVariant,
    onSurfaceVariant = IgLightOnSurfaceVariant,
    outline = IgLightOutline,
    outlineVariant = IgLightOutlineVariant,
    error = IgError,
    onError = Color.White,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = IgDarkOnSurface,
    onPrimary = IgDarkBackground,
    secondary = IgBlueLink,
    onSecondary = Color.White,
    tertiary = Color(0xFFDD2A7B),
    onTertiary = Color.White,
    background = IgDarkBackground,
    onBackground = IgDarkOnSurface,
    surface = IgDarkSurface,
    onSurface = IgDarkOnSurface,
    surfaceVariant = IgDarkSurfaceVariant,
    onSurfaceVariant = IgDarkOnSurfaceVariant,
    outline = IgDarkOutline,
    outlineVariant = Color(0xFF2A2A2A),
    error = IgError,
    onError = Color.White,
    scrim = Color.Black,
)

private val MetaShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

val MetaCardShape = RoundedCornerShape(16.dp)
val MetaCameraShape = RoundedCornerShape(12.dp)
val MetaPillShape = RoundedCornerShape(50)
val MetaMetricShape = RoundedCornerShape(12.dp)
val MetaSegmentShape = RoundedCornerShape(11.dp)

object MetaSemantic {
    val success = IgSuccess
    val warning = IgWarning
    val error = IgError
    val link = IgBlueLink
}

@Composable
fun MetaAthleticTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val defaults = Typography()
    val typography = defaults.copy(
        displayLarge = defaults.displayLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        displayMedium = defaults.displayMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        displaySmall = defaults.displaySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        titleLarge = defaults.titleLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        titleMedium = defaults.titleMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        titleSmall = defaults.titleSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal),
        bodySmall = defaults.bodySmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal),
        labelLarge = defaults.labelLarge.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
        labelMedium = defaults.labelMedium.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
        labelSmall = defaults.labelSmall.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium),
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    CompositionLocalProvider(LocalMetaGradient provides MetaGradientBrush) {
        MaterialTheme(
            colorScheme = colors,
            typography = typography,
            shapes = MetaShapes,
            content = content,
        )
    }
}
