package com.example.ts3.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 品牌色（与 res/values/colors.xml 保持一致）
val Brand = Color(0xFF2E7D6B)
val BrandDark = Color(0xFF1E2A38)
val BrandContainer = Color(0xFFB2DFDB)
val OnBrand = Color(0xFF1D332E)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandContainer,
    onPrimaryContainer = OnBrand,
    secondary = BrandDark,
    background = Color(0xFFF4F7F6),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6ECE9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82D5C0),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Brand,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFB0BEC5),
    background = Color(0xFF12181C),
    surface = Color(0xFF1B2328),
    surfaceVariant = Color(0xFF2A333A),
)

@Composable
fun Ts3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
