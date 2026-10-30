package com.weartuner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Gauge / note colours. Bright on a pure black OLED background. */
object TunerColors {
    val InTune = Color(0xFF3DDC84)
    val Close = Color(0xFFFFB300)
    val Off = Color(0xFFFF5252)
    val NoSignal = Color(0xFF6B6B6B)
    val Track = Color(0xFF2A2A2A)
    val Tick = Color(0xFF8A8A8A)
}

private val WearTunerColors = Colors(
    primary = Color(0xFF8AB4F8),
    primaryVariant = Color(0xFF3C5A8A),
    secondary = Color(0xFF5BD6A8),
    secondaryVariant = Color(0xFF1F6F52),
    background = Color.Black,
    surface = Color(0xFF1B1B1B),
    error = Color(0xFFFF5252),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFC4C7C5),
    onError = Color.Black,
)

@Composable
fun WearTunerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colors = WearTunerColors, content = content)
}
