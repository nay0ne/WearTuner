package com.weartuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.weartuner.engine.TunerState
import com.weartuner.ui.TunerViewModel
import com.weartuner.ui.WearTunerApp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: TunerViewModel by viewModels()

    private var permissionRequested = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onPermissionResult(granted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mic is only open while STARTED; stop releases it, start resumes if appropriate.
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> handleStart()
                    Lifecycle.Event.ON_STOP -> {
                        viewModel.onHostStop()
                        setKeepScreenOn(false)
                    }
                    else -> Unit
                }
            },
        )

        // Keep the screen on only while actively listening (bounded by the 30 s auto-stop).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state
                    .map { it is TunerState.Listening }
                    .distinctUntilChanged()
                    .collect { setKeepScreenOn(it) }
            }
        }

        setContent {
            WearTunerApp(viewModel = viewModel, onTapToTune = ::onTapToTune)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun handleStart() {
        val granted = hasMicPermission()
        viewModel.onHostStart(granted)
        if (!granted && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun onTapToTune() {
        if (hasMicPermission()) {
            viewModel.onTapToTune()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun setKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
