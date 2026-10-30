# WearTuner

A standalone guitar tuner for Wear OS. Kotlin and Jetpack Compose for Wear OS only, with no native code and no paid or copyleft dependencies. See [ARCHITECTURE.md](ARCHITECTURE.md) for the design decisions.

## What it does

- Listens through the watch microphone and shows the nearest note (or the nearest string of the selected tuning), the offset in cents, and the frequency in Hz.
- A curved gauge along the bezel maps -50..+50 cents. Green is within 3 cents, amber within 10, red beyond, grey when there is no signal.
- Tuning presets: Chromatic, Standard, Drop D, Half-step down, DADGAD, Open G.
- Two pitch detectors, selectable in settings: MPM (default) and YIN.
- Adjustable A4 reference (430-450 Hz) and an optional haptic click when a string is in tune.

## Open in Android Studio

1. Install Android Studio (Ladybug or newer) with JDK 17 and the Android SDK 36 platform.
2. Choose File > Open and select this folder. Gradle sync uses the wrapper (Gradle 8.13). Android Studio writes `local.properties` with your SDK path.
3. Use the `@Preview` functions in `ui/TunerScreen.kt` and `ui/SettingsScreen.kt` (small round 192 dp and large round 227 dp) to check the layout.

Command line: `./gradlew testDebugUnitTest assembleDebug` (requires `ANDROID_HOME` or `local.properties` with `sdk.dir`).

## Run on an emulator or a watch

Emulator: in Device Manager create a Wear OS device (small or large round), start it, and press Run.

Physical watch (ADB over Wi-Fi):
1. On the watch, open Settings > About > Versions and tap the build number seven times to enable Developer options.
2. In Settings > Developer options enable ADB debugging and Debug over Wi-Fi. Note the IP address and port shown.
3. Connect the watch and your computer to the same Wi-Fi network, then run `adb connect <ip>:<port>`. On first connection, accept the pairing or debugging prompt on the watch (newer watches use "Pair new device" with `adb pair <ip>:<pairing-port>`).
4. Select the watch in Android Studio and press Run, or use `./gradlew installDebug`.

## Battery design

- No service and no background capture. The microphone is open only while the activity is started; `ON_STOP` releases `AudioRecord`, and `ON_START` resumes only if listening was active and not auto-stopped.
- Auto-stop after 30 s without a valid pitch: the mic is released and the screen shows "Tap to tune".
- Silence gate: windows with RMS below 0.003 (about -50 dBFS) skip pitch detection entirely.
- 16 kHz mono float capture (22.05 and 44.1 kHz fallbacks), 2048-sample windows with a 1024 hop (about 15 analyses per second), lag search limited to 60-1400 Hz, direct time-domain loops with no FFT.
- Capture and DSP share one dedicated thread at `THREAD_PRIORITY_AUDIO`; all DSP buffers are allocated once per detector.
- The screen is kept on only while listening, which the 30 s auto-stop bounds. Ambient mode is not declared. Black background for OLED.
- Haptics fire once after 250 ms within +-3 cents and re-arm only after leaving +-8 cents or changing target note.

## Tests

JVM unit tests (`app/src/test`) cover MPM and YIN accuracy for every preset open-string pitch (pure, harmonic with decay, and noisy signals), silence handling, note math, tuning matching, smoothing, and haptic hysteresis. The `dsp/` and `tuning/` packages have no Android imports.

## Licence

Apache License 2.0. See [LICENSE](LICENSE).

## Deviations

- The manifest theme is `@style/Theme.WearTuner`, a child of `@android:style/Theme.DeviceDefault` that only sets a black window background (avoids a white flash on launch).
- Wear Compose Material 1 has no `AppScaffold`, so a single `Scaffold` with a curved `TimeText` wraps the `SwipeDismissableNavHost`. The time scrolls away on the settings list and a `PositionIndicator` is shown there.
- Launch behaviour: opening the app starts listening immediately (when the permission is held) rather than waiting for a tap. "Tap to tune" appears after an auto-stop or when the mic permission is missing. The `Stopped` state is shown only after capture ends for an error.
- The "reset on note change over 50 cents" rule in `PitchSmoother` is applied to the median output, so a single outlier cannot trigger a reset.
- Extra files not in the spec's layout: `engine/TunerState.kt`, `engine/HapticTrigger.kt` (pure, unit-tested hysteresis logic), `ui/WearTunerApp.kt` (navigation host).
- `androidx.wear:wear-tooling-preview:1.0.0` is added for `WearDevices`.
