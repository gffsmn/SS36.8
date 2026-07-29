package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmoledDarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFE2C55F), // Accent color like yellow/orange to look like warning signs
    secondary = Color(0xFFB19B4C),
    tertiary = Color(0xFFF44336), // Red for critical alerts
    background = Color.Black,
    surface = Color(0xFF121212),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force AMOLED
  dynamicColor: Boolean = false, // Disable dynamic colors for AMOLED strict consistency
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = AmoledDarkColorScheme, typography = Typography, content = content)
}
