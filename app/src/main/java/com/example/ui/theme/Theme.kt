package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    secondary = SecondaryDark,
    tertiary = TertiaryDark,
    background = FeltGreyMidnight,
    surface = Color(0xFF1A1C1E),
    onPrimary = Color(0xFF00315B),
    onSecondary = Color(0xFFE2E2E6),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    secondary = SecondaryLight,
    tertiary = TertiaryLight,
    background = Color(0xFF0F1113),
    surface = Color(0xFF1A1C1E),
    onPrimary = Color(0xFFD1E4FF),
    onSecondary = Color.White,
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for the minimalist gaming vibe
    dynamicColor: Boolean = false, // Set false to ensure our exact custom aesthetic is delivered
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
