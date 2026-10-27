package com.example.pelletswaterheater.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6200EE),
    secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
    background = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF000000),
    onBackground = androidx.compose.ui.graphics.Color(0xFF000000),
    onSurface = androidx.compose.ui.graphics.Color(0xFF000000),
)

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFBB86FC),     // lighter purple for dark theme
    secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),   // same teal secondary
    background = androidx.compose.ui.graphics.Color(0xFF121212),  // typical dark background
    surface = androidx.compose.ui.graphics.Color(0xFF121212),     // match background for surface
    onPrimary = androidx.compose.ui.graphics.Color(0xFF000000),   // dark text/icons on light primary
    onSecondary = androidx.compose.ui.graphics.Color(0xFF000000), // dark text/icons on secondary
    onBackground = androidx.compose.ui.graphics.Color(0xFFFFFFFF),// white text on dark background
    onSurface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)    // white text on dark surface
)

@Composable
fun PelletsWaterHeaterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}