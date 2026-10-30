package com.weartuner.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.tooling.preview.devices.WearDevices
import com.weartuner.R
import com.weartuner.dsp.NoteMath
import com.weartuner.dsp.PitchAlgorithm
import com.weartuner.settings.Settings
import com.weartuner.tuning.Tunings
import com.weartuner.ui.theme.WearTunerTheme
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: Settings,
    onTuningSelected: (String) -> Unit,
    onAlgorithmSelected: (PitchAlgorithm) -> Unit,
    onA4Changed: (Int) -> Unit,
    onHapticsChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    listState: ScalingLazyListState = rememberScalingLazyListState(),
) {
    // The default ScalingLazyColumn behaviour in wear-compose 1.4 already handles rotary input.
    ScalingLazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(top = 32.dp, bottom = 32.dp, start = 10.dp, end = 10.dp),
    ) {
        item { ListHeader { Text(stringResource(R.string.settings_tuning)) } }
        items(count = Tunings.all.size) { i ->
            val tuning = Tunings.all[i]
            val selected = tuning.id == settings.tuningId
            ToggleChip(
                checked = selected,
                onCheckedChange = { onTuningSelected(tuning.id) },
                label = { Text(tuning.name) },
                secondaryLabel = {
                    Text(
                        if (tuning.isChromatic) {
                            stringResource(R.string.chromatic_hint)
                        } else {
                            tuning.midiNotes.joinToString(" ") { NoteMath.noteName(it) }
                        },
                    )
                },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.radioIcon(selected),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { ListHeader { Text(stringResource(R.string.settings_algorithm)) } }
        items(count = PitchAlgorithm.entries.size) { i ->
            val algorithm = PitchAlgorithm.entries[i]
            val selected = algorithm == settings.algorithm
            ToggleChip(
                checked = selected,
                onCheckedChange = { onAlgorithmSelected(algorithm) },
                label = { Text(algorithm.label) },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.radioIcon(selected),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item { ListHeader { Text(stringResource(R.string.settings_reference)) } }
        item {
            Text(
                text = stringResource(R.string.settings_a4_format, settings.a4Hz),
                color = MaterialTheme.colors.onBackground,
            )
        }
        item {
            InlineSlider(
                value = settings.a4Hz.toFloat(),
                onValueChange = { onA4Changed(it.roundToInt()) },
                increaseIcon = { Text("+") },
                decreaseIcon = { Text("−") },
                valueRange = Settings.MIN_A4_HZ.toFloat()..Settings.MAX_A4_HZ.toFloat(),
                steps = Settings.MAX_A4_HZ - Settings.MIN_A4_HZ - 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            ToggleChip(
                checked = settings.hapticsEnabled,
                onCheckedChange = onHapticsChanged,
                label = { Text(stringResource(R.string.settings_haptics)) },
                secondaryLabel = { Text(stringResource(R.string.settings_haptics_summary)) },
                toggleControl = {
                    Icon(
                        imageVector = ToggleChipDefaults.switchIcon(settings.hapticsEnabled),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun SettingsSmallRoundPreview() {
    WearTunerTheme { SettingsScreen(Settings(), {}, {}, {}, {}) }
}

@Preview(device = WearDevices.LARGE_ROUND, showSystemUi = true)
@Composable
private fun SettingsLargeRoundPreview() {
    WearTunerTheme { SettingsScreen(Settings(tuningId = "drop_d", a4Hz = 442), {}, {}, {}, {}) }
}
