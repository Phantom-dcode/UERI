package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CleanMinimalismColorScheme = lightColorScheme(
    primary = EmergencyRed,
    onPrimary = Color.White,
    primaryContainer = EmergencyRedLight,
    onPrimaryContainer = EmergencyRedDark,
    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = BrandBlueLight,
    onSecondaryContainer = BrandBlue,
    tertiary = SafetyGreen,
    onTertiary = Color.White,
    tertiaryContainer = SafetyGreenLight,
    onTertiaryContainer = SafetyGreenDark,
    background = CleanCanvas,
    onBackground = TextSlate900,
    surface = CleanWhite,
    onSurface = TextSlate900,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextSlate600,
    outline = SlateBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CleanMinimalismColorScheme,
        typography = Typography,
        content = content
    )
}


