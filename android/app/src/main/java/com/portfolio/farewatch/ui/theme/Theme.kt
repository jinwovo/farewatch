package com.portfolio.farewatch.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FarewatchColors = lightColorScheme(
    primary = Ink,
    onPrimary = CanvasWhite,
    secondary = Coral,
    onSecondary = CanvasWhite,
    background = CanvasWhite,
    onBackground = Ink,
    surface = CanvasWhite,
    onSurface = Ink,
    surfaceVariant = Surface,
    onSurfaceVariant = Steel,
    error = ErrorRed,
    onError = CanvasWhite,
    outline = Hairline,
    outlineVariant = Hairline,
)

@Composable
fun FarewatchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FarewatchColors,
        typography = FarewatchTypography,
        content = content,
    )
}
