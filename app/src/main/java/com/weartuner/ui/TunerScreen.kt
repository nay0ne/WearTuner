package com.weartuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.weartuner.R
import com.weartuner.dsp.NoteMath
import com.weartuner.engine.TunerState
import com.weartuner.tuning.Tuning
import com.weartuner.tuning.Tunings
import com.weartuner.ui.components.CentsGauge
import com.weartuner.ui.components.centsColor
import com.weartuner.ui.theme.WearTunerTheme
import java.util.Locale
import kotlin.math.roundToInt

/** Fraction of the screen width inset on each side to stay inside the inscribed square. */
private const val SAFE_INSET = 0.146f

/** One string change per bezel/crown gesture burst. */
private const val ROTARY_DEBOUNCE_MS = 250L

/**
 * In a preset tuning the screen measures against one string at a time: tap (or turn the bezel)
 * to advance to the next string. In chromatic mode it shows the nearest note.
 */
@Composable
fun TunerScreen(
    state: TunerState,
    tuning: Tuning,
    stringIndex: Int,
    onTapToTune: () -> Unit,
    onNextString: () -> Unit,
    onPreviousString: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val inset = (widthDp * SAFE_INSET).dp
    val centsSize = (widthDp * 0.095f).sp
    val smallSize = (widthDp * 0.058f).sp

    val listening = state as? TunerState.Listening
    val stopped = listening == null
    val signal = listening?.signal == true
    val cents = listening?.cents ?: 0f
    val color = centsColor(cents, signal)

    val targetMidi = tuning.midiNotes.getOrNull(stringIndex)
    val preset = targetMidi != null
    // Smaller note in preset mode to leave room for the string row.
    val noteSize = (widthDp * if (preset) 0.19f else 0.22f).sp
    val noteName = targetMidi?.let { NoteMath.noteName(it) } ?: listening?.noteName
    val octave = targetMidi?.let { NoteMath.octave(it) } ?: listening?.octave
    // A preset reading belongs to the string it was taken on, so hide it once the signal drops.
    val showCents = listening?.noteName != null && (signal || !preset)
    val canAdvance = preset && !stopped

    val focusRequester = remember { FocusRequester() }
    val lastRotaryMs = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onRotaryScrollEvent { event ->
                if (!canAdvance) return@onRotaryScrollEvent false
                if (event.verticalScrollPixels != 0f &&
                    event.uptimeMillis - lastRotaryMs.longValue >= ROTARY_DEBOUNCE_MS
                ) {
                    lastRotaryMs.longValue = event.uptimeMillis
                    if (event.verticalScrollPixels > 0f) onNextString() else onPreviousString()
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable()
            .clickable(
                enabled = stopped || canAdvance,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = if (stopped) onTapToTune else onNextString,
            ),
    ) {
        CentsGauge(cents = cents, signal = signal && !stopped)

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = inset, end = inset, bottom = (widthDp * 0.08f).dp)
                .alpha(
                    when {
                        stopped -> 0.18f
                        signal -> 1f
                        else -> 0.4f
                    },
                )
                .semantics {
                    if (noteName != null) {
                        val reading = if (showCents) ", ${cents.roundToInt()} cents" else ""
                        contentDescription = if (preset) {
                            "String ${tuning.midiNotes.size - stringIndex}, $noteName$octave$reading"
                        } else {
                            "$noteName$octave$reading"
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (preset) StringRow(tuning, stringIndex, fontSize = smallSize)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = noteName ?: stringResource(R.string.no_note),
                    color = color,
                    fontSize = noteSize,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                octave?.let {
                    Text(
                        text = it.toString(),
                        color = color,
                        fontSize = smallSize * 1.3f,
                        modifier = Modifier.padding(start = 2.dp, bottom = (widthDp * 0.04f).dp),
                    )
                }
            }
            Text(
                text = if (showCents) String.format(Locale.US, "%+d¢", cents.roundToInt()) else " ",
                color = color,
                fontSize = centsSize,
                maxLines = 1,
            )
            Text(
                text = if (preset && signal && listening?.inTune == true) {
                    stringResource(R.string.tap_next_string)
                } else {
                    hzText(listening)
                },
                color = MaterialTheme.colors.onSurfaceVariant,
                fontSize = smallSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }

        if (stopped) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = inset, vertical = (widthDp * 0.08f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state is TunerState.NeedsPermission) {
                    Text(
                        text = stringResource(R.string.permission_needed),
                        color = MaterialTheme.colors.onBackground,
                        fontSize = centsSize,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.permission_tap),
                        color = MaterialTheme.colors.primary,
                        fontSize = centsSize,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.tap_to_tune),
                        color = MaterialTheme.colors.onBackground,
                        fontSize = (widthDp * 0.12f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        CompactChip(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (widthDp * 0.17f).dp),
            label = {
                Text(
                    text = tuning.name,
                    fontSize = (widthDp * 0.06f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            colors = ChipDefaults.secondaryChipColors(),
        )
    }
}

/** The tuning's strings, lowest first, with the one being tuned highlighted. */
@Composable
private fun StringRow(tuning: Tuning, current: Int, fontSize: TextUnit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tuning.midiNotes.forEachIndexed { i, midi ->
            val selected = i == current
            Text(
                text = NoteMath.noteName(midi),
                color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.onSurfaceVariant,
                fontSize = fontSize,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.alpha(if (selected) 1f else 0.6f),
            )
        }
    }
}

private fun hzText(s: TunerState.Listening?): String {
    val hz = s?.frequencyHz ?: return " "
    return String.format(Locale.US, "%.1f Hz", hz)
}

private val previewListening = TunerState.Listening(
    noteName = "A", octave = 2, cents = -6f, frequencyHz = 109.4f,
    inTune = false, signal = true,
)

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun TunerSmallRoundPreview() {
    WearTunerTheme { TunerScreen(previewListening, Tunings.STANDARD, 1, {}, {}, {}, {}) }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun TunerLargeRoundPreview() {
    WearTunerTheme {
        TunerScreen(
            previewListening.copy(cents = 1.5f, inTune = true),
            Tunings.HALF_STEP_DOWN, 1, {}, {}, {}, {},
        )
    }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun TunerChromaticLargeRoundPreview() {
    WearTunerTheme { TunerScreen(previewListening, Tunings.CHROMATIC, 0, {}, {}, {}, {}) }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun TunerStoppedSmallRoundPreview() {
    WearTunerTheme { TunerScreen(TunerState.AutoStopped, Tunings.STANDARD, 0, {}, {}, {}, {}) }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun TunerPermissionLargeRoundPreview() {
    WearTunerTheme { TunerScreen(TunerState.NeedsPermission, Tunings.CHROMATIC, 0, {}, {}, {}, {}) }
}
