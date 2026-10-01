package com.iiankehn.slater2.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val CoreBlue = Color(0xFF0072BC)
val CoreBlueLight = Color(0xFF72C7FF)
val Midnight = Color(0xFF080C11)
val SlateSurface = Color(0xFF10161D)
val SlateSurfaceSoft = Color(0xFF151D26)
val SlateSurfaceRaised = Color(0xFF1B2530)
val SlateBorder = Color(0xFF2A3744)
val SlateText = Color(0xFFF1F5F9)
val SlateTextMuted = Color(0xFFA7B4C2)

private val SlateColors = darkColorScheme(
    primary = CoreBlueLight,
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF004B73),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = Color(0xFFB8C8D8),
    onSecondary = Color(0xFF23323F),
    secondaryContainer = Color(0xFF344956),
    onSecondaryContainer = Color(0xFFD4E5F5),
    background = Midnight,
    onBackground = SlateText,
    surface = SlateSurface,
    onSurface = SlateText,
    surfaceVariant = SlateSurfaceRaised,
    onSurfaceVariant = SlateTextMuted,
    surfaceContainer = SlateSurfaceSoft,
    surfaceContainerHigh = SlateSurfaceRaised,
    surfaceContainerHighest = Color(0xFF24313E),
    outline = Color(0xFF4A5A69),
    outlineVariant = SlateBorder,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    scrim = Color.Black,
)

private val SlateTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

private val SlateShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun SlateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SlateColors,
        typography = SlateTypography,
        shapes = SlateShapes,
        content = content,
    )
}
