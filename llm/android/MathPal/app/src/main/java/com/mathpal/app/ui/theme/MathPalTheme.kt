package com.mathpal.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WarmBlue = Color(0xFF4A90D9)
private val WarmBlueDark = Color(0xFF2C5F9E)
private val WarmBlueLight = Color(0xFFD6E8FA)
private val Orange = Color(0xFFF5A623)
private val OrangeLight = Color(0xFFFFF0D6)
private val SurfaceWhite = Color(0xFFFCFCFF)
private val BackgroundWhite = Color(0xFFF8F9FC)
private val ErrorRed = Color(0xFFD32F2F)
private val SuccessGreen = Color(0xFF43A047)

val MathPalColors = lightColorScheme(
    primary = WarmBlue,
    onPrimary = Color.White,
    primaryContainer = WarmBlueLight,
    onPrimaryContainer = WarmBlueDark,
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = OrangeLight,
    onSecondaryContainer = Color(0xFF5D3A00),
    surface = SurfaceWhite,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE8EAF0),
    onSurfaceVariant = Color(0xFF44464F),
    background = BackgroundWhite,
    onBackground = Color(0xFF1C1B1F),
    error = ErrorRed,
    onError = Color.White,
    outline = Color(0xFFBCC3CE),
)

val MathPalGreen = SuccessGreen

val MathPalTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 36.sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 32.sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 16.sp,
    ),
)

val MathPalShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun MathPalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MathPalColors,
        typography = MathPalTypography,
        shapes = MathPalShapes,
        content = content,
    )
}
