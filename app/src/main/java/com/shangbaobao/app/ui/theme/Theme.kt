package com.shangbaobao.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF155EEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF071D49),
    secondary = Color(0xFF475467),
    secondaryContainer = Color(0xFFE4E7EC),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F4F7),
    onSurface = Color(0xFF101828),
    onSurfaceVariant = Color(0xFF475467),
    error = Color(0xFFD92D20),
    errorContainer = Color(0xFFFEE4E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DB5FF),
    primaryContainer = Color(0xFF174EA6),
    background = Color(0xFF0C111D),
    surface = Color(0xFF161B26),
    surfaceVariant = Color(0xFF1F242F),
)

@Composable
fun ShangBaoBaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

