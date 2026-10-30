package com.weartuner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.scrollAway
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.weartuner.tuning.Tunings
import com.weartuner.ui.theme.WearTunerTheme

private const val ROUTE_TUNER = "tuner"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun WearTunerApp(
    viewModel: TunerViewModel,
    onTapToTune: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val stringIndex by viewModel.stringIndex.collectAsStateWithLifecycle()

    val navController = rememberSwipeDismissableNavController()
    val settingsListState = rememberScalingLazyListState()
    var onSettingsRoute by remember { mutableStateOf(false) }

    WearTunerTheme {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            timeText = {
                TimeText(
                    modifier = if (onSettingsRoute) Modifier.scrollAway(settingsListState) else Modifier,
                )
            },
            positionIndicator = {
                if (onSettingsRoute) PositionIndicator(scalingLazyListState = settingsListState)
            },
        ) {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = ROUTE_TUNER,
            ) {
                composable(ROUTE_TUNER) {
                    TunerScreen(
                        state = state,
                        tuning = Tunings.byId(settings.tuningId),
                        stringIndex = stringIndex,
                        onTapToTune = onTapToTune,
                        onNextString = viewModel::nextString,
                        onPreviousString = viewModel::previousString,
                        onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                    )
                }
                composable(ROUTE_SETTINGS) {
                    DisposableEffect(Unit) {
                        onSettingsRoute = true
                        onDispose { onSettingsRoute = false }
                    }
                    SettingsScreen(
                        settings = settings,
                        onTuningSelected = { viewModel.setTuning(it) },
                        onAlgorithmSelected = { viewModel.setAlgorithm(it) },
                        onA4Changed = { viewModel.setA4(it) },
                        onHapticsChanged = { viewModel.setHaptics(it) },
                        listState = settingsListState,
                    )
                }
            }
        }
    }
}
