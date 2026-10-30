package com.weartuner.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.weartuner.ui.theme.TunerColors
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Green within +-3 cents, amber within +-10, red beyond, grey without signal. */
fun centsColor(cents: Float, signal: Boolean): Color = when {
    !signal -> TunerColors.NoSignal
    abs(cents) <= 3f -> TunerColors.InTune
    abs(cents) <= 10f -> TunerColors.Close
    else -> TunerColors.Off
}

private const val SWEEP_DEGREES = 140f
private const val CENTER_ANGLE = 90f // bottom of the screen in canvas coordinates
private val TICKS = floatArrayOf(-50f, -25f, -10f, 0f, 10f, 25f, 50f)

/** Canvas angle in degrees for a cents value: -50 at the left end, +50 at the right end. */
private fun angleFor(cents: Float): Double =
    Math.toRadians((CENTER_ANGLE - (cents.coerceIn(-50f, 50f) / 50f) * (SWEEP_DEGREES / 2f)).toDouble())

/**
 * Arc hugging the bottom bezel (140 degree sweep) mapping -50..+50 cents, with tick marks at
 * 0, +-10, +-25, +-50 and an animated dot indicator.
 */
@Composable
fun CentsGauge(
    cents: Float,
    signal: Boolean,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = cents.coerceIn(-50f, 50f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "cents",
    )
    val dotColor = centsColor(cents, signal)

    Canvas(modifier = modifier.fillMaxSize()) {
        val stroke = 4.dp.toPx()
        val margin = 8.dp.toPx()
        val diameter = min(size.width, size.height) - 2 * margin
        val radius = diameter / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawArc(
            color = TunerColors.Track,
            startAngle = CENTER_ANGLE - SWEEP_DEGREES / 2f,
            sweepAngle = SWEEP_DEGREES,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(diameter, diameter),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )

        for (tick in TICKS) {
            val a = angleFor(tick)
            val length = if (tick == 0f) 11.dp.toPx() else 6.dp.toPx()
            val outer = radius - stroke
            val inner = outer - length
            drawLine(
                color = if (tick == 0f) Color.White else TunerColors.Tick,
                start = Offset(center.x + (outer * cos(a)).toFloat(), center.y + (outer * sin(a)).toFloat()),
                end = Offset(center.x + (inner * cos(a)).toFloat(), center.y + (inner * sin(a)).toFloat()),
                strokeWidth = if (tick == 0f) 2.dp.toPx() else 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        val a = angleFor(animated)
        drawCircle(
            color = dotColor,
            radius = 6.dp.toPx(),
            center = Offset(center.x + (radius * cos(a)).toFloat(), center.y + (radius * sin(a)).toFloat()),
        )
    }
}
