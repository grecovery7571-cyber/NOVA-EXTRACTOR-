package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = WhitePure,
    secondary = GreyOff,
    tertiary = NeonBlue,
    background = BlackPure,
    surface = GreyDark,
    onPrimary = BlackPure,
    onSecondary = WhitePure,
    onTertiary = BlackPure,
    onBackground = WhitePure,
    onSurface = WhitePure
  )

private val LightColorScheme = DarkColorScheme // Enforce high-contrast dark style throughout

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme for the high-contrast aesthetic
  dynamicColor: Boolean = false, // Disable dynamic colors to preserve exact custom black branding branding
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
