package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

import androidx.core.view.WindowCompat

enum class AppThemeMode {
    SPOTIFY_DARK,
    AMOLED_PURE,
    NEON_CYBERPUNK,
    CLEAN_LIGHT
}

val SpotifyDarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreen.copy(alpha = 0.2f),
    onPrimaryContainer = SpotifyGreenBright,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = LightText,
    surface = DarkSurface,
    onSurface = LightText,
    surfaceVariant = DarkElevatedSurface,
    onSurfaceVariant = MutedText,
    outline = Color(0xFF383838)
)

val AmoledPureColorScheme = darkColorScheme(
    primary = SpotifyGreenBright,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreen.copy(alpha = 0.3f),
    onPrimaryContainer = Color.White,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    background = AmoledBlack,
    onBackground = Color.White,
    surface = Color(0xFF0F0F0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF2A2A2A)
)

val CyberpunkColorScheme = darkColorScheme(
    primary = RoyalPurple,
    onPrimary = Color.White,
    primaryContainer = RoyalPurple.copy(alpha = 0.3f),
    onPrimaryContainer = NeonCyan,
    secondary = NeonCyan,
    onSecondary = Color.Black,
    background = Color(0xFF0A0518),
    onBackground = Color.White,
    surface = Color(0xFF140A2E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF211047),
    onSurfaceVariant = Color(0xFFD3C8EE),
    outline = Color(0xFF3B1E78)
)

val CleanLightColorScheme = lightColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.White,
    primaryContainer = SpotifyGreen.copy(alpha = 0.15f),
    onPrimaryContainer = Color(0xFF006C35),
    secondary = Color(0xFF008080),
    onSecondary = Color.White,
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF121212),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF555555),
    outline = Color(0xFFE0E0E0)
)

@Composable
fun AuraTheme(
    themeMode: AppThemeMode = AppThemeMode.SPOTIFY_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeMode) {
        AppThemeMode.SPOTIFY_DARK -> SpotifyDarkColorScheme
        AppThemeMode.AMOLED_PURE -> AmoledPureColorScheme
        AppThemeMode.NEON_CYBERPUNK -> CyberpunkColorScheme
        AppThemeMode.CLEAN_LIGHT -> CleanLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val isLight = themeMode == AppThemeMode.CLEAN_LIGHT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
