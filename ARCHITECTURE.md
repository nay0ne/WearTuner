# WearTuner — Architecture Spec

Standalone Wear OS guitar tuner. Kotlin only, no native code, no paid or copyleft dependencies
(everything is AndroidX / Apache-2.0).

## Decisions (fixed)

| Area | Decision | Why |
|---|---|---|
| Audio input | Platform `AudioRecord`, mono, `ENCODING_PCM_FLOAT`, **16 000 Hz** | Low rate = less CPU/battery. Guitar fundamentals are 65–1320 Hz, so 16 kHz is plenty. 16 kHz is the most reliably supported rate on watch mics. |
| Audio source | `MediaRecorder.AudioSource.UNPROCESSED` if `AudioManager.getProperty(PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"`, else `VOICE_RECOGNITION` | Avoids AGC/noise suppression that distorts pitch. |
| Pitch detection | `PitchDetector` interface with two implementations: **MPM** (default) and **YIN**, switchable in settings | User request. MPM has fewer octave errors on plucked strings. |
| Threading | Capture + DSP on one dedicated background thread (`newSingleThreadContext` or a `HandlerThread` with `Process.THREAD_PRIORITY_AUDIO`); UI receives `StateFlow` | Never block the main thread; one thread keeps the wake-up count low. |
| UI | Jetpack Compose for Wear OS (`androidx.wear.compose:compose-material`, `compose-foundation`, `compose-navigation`). No Horologist. | Covers everything needed with fewer dependencies. |
| Settings storage | `androidx.datastore:datastore-preferences` | Free, async, tiny. |
| minSdk / target | minSdk 30 (Wear OS 3), compile/target 36 (Wear OS 6) | Play requires target 35+ for Wear OS; primary device is Galaxy Watch 7 on One UI 9 Watch (Wear OS 7). |

### Pinned toolchain (bump only if the build fails)
Gradle wrapper 8.10.2 · AGP 8.7.3 · Kotlin 2.0.21 (+ `org.jetbrains.kotlin.plugin.compose`) · JDK 17
Compose BOM 2024.12.01 · wear-compose 1.4.0 · activity-compose 1.9.3 · lifecycle 2.8.7 ·
datastore-preferences 1.1.1 · core-ktx 1.15.0 · coroutines 1.9.0 · junit 4.13.2.
Use a `gradle/libs.versions.toml` version catalog and Kotlin DSL build files.

## Battery strategy (core requirement)

1. **No service, no background capture.** The mic is open only while the activity is STARTED.
   `ON_STOP` releases `AudioRecord`, and `ON_START` resumes if we were listening before and are
   not auto-stopped.
2. **Auto-stop on silence:** if no valid pitch has been detected for **30 s**, stop capture, release
   the mic, and show "Tap to tune". Tapping the screen restarts.
3. **Silence gate:** compute RMS before running the detector. If it is below roughly −50 dBFS
   (constant `SILENCE_RMS = 0.003f`), skip pitch detection entirely.
4. **Keep screen on only while listening:** set `FLAG_KEEP_SCREEN_ON` while capturing and clear it
   when stopped. This is bounded by the 30 s auto-stop. Ambient mode is not declared, so going to
   the watch face triggers `ON_STOP`.
5. **Zero allocation in the hot loop:** pre-allocate all DSP buffers once per detector instance.
6. **Haptics:** one short `VibrationEffect.EFFECT_CLICK` (fallback: 30 ms one-shot) when the note
   enters ±3 cents and stays there for at least 250 ms. Re-arm only after it leaves ±8 cents or the
   target note changes (hysteresis prevents buzzing). Can be turned off in settings.

## Package layout (`com.weartuner`)

```
app/src/main/java/com/weartuner/
  MainActivity.kt              // permission, lifecycle wiring, keep-screen-on, setContent
  audio/AudioCapture.kt        // AudioRecord wrapper; start()/stop(); emits analysis windows
  dsp/PitchDetector.kt         // interface + PitchResult(frequencyHz, confidence)
  dsp/MpmDetector.kt
  dsp/YinDetector.kt
  dsp/Rms.kt
  dsp/NoteMath.kt              // freq <-> midi, note name, cents; A4 reference param
  dsp/PitchSmoother.kt         // median-of-5 on Hz, then EMA on cents; 400 ms hold
  tuning/Tuning.kt             // presets + nearest-target matcher
  engine/TunerEngine.kt        // owns capture loop, detector, smoother, silence timer -> StateFlow<TunerState>
  engine/Haptics.kt
  settings/SettingsRepository.kt  // DataStore: tuning id, algorithm, a4Hz (430..450), haptics
  ui/TunerViewModel.kt
  ui/TunerScreen.kt
  ui/SettingsScreen.kt
  ui/components/CentsGauge.kt
  ui/theme/Theme.kt
app/src/test/java/com/weartuner/  // JVM unit tests (no Android deps in dsp/ & tuning/)
```

`dsp/` and `tuning/` must be **pure Kotlin with no Android imports**, so they can be unit-tested
on the JVM.

## Audio pipeline

- Window size N = **2048** samples (128 ms) with a hop of **1024** (50% overlap), which gives about
  15 analyses per second. Keep a sliding window: read 1024 samples, shift, and analyse.
- `AudioRecord` buffer = `max(getMinBufferSize * 2, 4096 * 4 bytes)`.
- Read with `READ_BLOCKING` into a float array. Check the return value for errors.
- Search range is **60–1400 Hz**, so lags are `sampleRate/1400 .. sampleRate/60` (11..267 at 16 kHz).
  Restrict the autocorrelation/difference loops to `maxLag`. This is far cheaper than a full O(N²)
  pass, so no FFT is needed. Use direct time-domain loops.
- If `AudioRecord` fails to initialise at 16 kHz, fall back to 22050, then 44100. Detectors take
  `sampleRate` as a constructor parameter.

### MPM (McLeod & Wyvill 2005)
NSDF n'(τ) = 2·r(τ) / m(τ), computed for τ in [0, maxLag], where m(τ) is updated incrementally.
Peak picking: for each positive region after the first zero-crossing, keep that region's maximum
(key maxima). Threshold = k · highestKeyMax with **k = 0.93**. Take the first key max above the
threshold, then apply parabolic interpolation. Confidence = the NSDF value at the peak. Reject the
result if confidence < 0.8.

### YIN (de Cheveigné & Kawahara 2002)
Difference function → cumulative mean normalised difference → absolute threshold **0.15** (take the
local minimum after the first dip below it) → parabolic interpolation. Confidence = 1 − d'(τ).
Reject the result if no dip falls below 0.15.

## Note math & tunings

- midi = 69 + 12·log2(f / A4), and cents = 100·(midi − round(midi)).
- Use sharps for note names (C, C#, D … B) plus an octave number.
- Presets (MIDI notes, low → high):
  - Chromatic (no targets: show nearest note)
  - Standard E2 A2 D3 G3 B3 E4
  - Drop D D2 A2 D3 G3 B3 E4
  - Half-step down D#2 G#2 C#3 F#3 A#3 D#4
  - DADGAD D2 A2 D3 G3 A3 D4
  - Open G D2 G2 D3 G3 B3 D4
- With a preset selected, choose the target string whose frequency is closest in cents to the
  detected pitch, then show the string name and the cents offset from that target.

## Smoothing

Median over the last 5 raw Hz values → convert to cents vs the target → EMA (α = 0.35).
If the detected note changes by more than 50 cents, reset the median and EMA. If detection is lost,
keep showing the last value for 400 ms, then mark the display "no signal" (dim the UI). Do not clear
it to blank immediately.

## TunerState

```kotlin
sealed interface TunerState {
  data object NeedsPermission
  data object Stopped               // initial / user paused
  data object AutoStopped           // 30 s silence
  data class Listening(
    val noteName: String?,          // null = no signal yet
    val octave: Int?,
    val cents: Float,               // -50..+50
    val frequencyHz: Float?,
    val inTune: Boolean,            // |cents| <= 3
    val signal: Boolean,
    val stringLabel: String?        // e.g. "6 · E2" in preset mode
  )
}
```

## UI (round screen)

- Wrap the app in `AppScaffold`/`Scaffold` with a curved `TimeText` at the top.
  `SwipeDismissableNavHost` has two routes: `tuner` and `settings`.
- **TunerScreen:**
  - `CentsGauge` is a Canvas arc hugging the bezel, about 140° sweep centred at the bottom
    (or top, avoiding `TimeText`), mapped to −50..+50 cents. Draw tick marks at 0, ±10, ±25, and
    ±50 cents, and a dot indicator that animates with `animateFloatAsState` (spring, no long tween).
  - Colours: green within ±3 cents, amber within ±10, red beyond that, and grey when there is no
    signal.
  - Centre: a large note name with a small octave, a cents value ("+4¢") underneath, and Hz in
    small text.
  - Bottom: a compact `Chip`/`CompactChip` showing the preset name, which opens Settings.
  - In Stopped or AutoStopped, overlay "Tap to tune". Tapping anywhere starts listening.
  - Size everything relative to the screen (`LocalConfiguration.screenWidthDp`), and keep text
    inside the inscribed square (about 14.6% inset per side). Test on small round (192 dp) and
    large round (227 dp) previews.
- **SettingsScreen:** a `ScalingLazyColumn` with tuning preset choices (radio `ToggleChip`s),
  algorithm (MPM/YIN), A4 reference (an `InlineSlider` from 430–450 in steps of 1), and a haptics
  toggle. It must support rotary-crown scrolling (`rotaryScrollable` / default behaviour in
  wear-compose 1.4).
- Respect the MaterialTheme for Wear, and use a black background for OLED power saving.

## Manifest

- `<uses-feature android:name="android.hardware.type.watch"/>`
- `<uses-feature android:name="android.hardware.microphone" android:required="true"/>`
- Permissions: `RECORD_AUDIO`, `VIBRATE`
- `<meta-data android:name="com.google.android.wearable.standalone" android:value="true"/>`
- Theme: `@android:style/Theme.DeviceDefault`. Single activity, `exported=true`, with a launcher
  intent filter.

## Tests (JVM, required)

- MPM and YIN: synthetic signals at 16 kHz with N = 2048 for every open-string pitch across all
  presets. Test a pure sine, and a sine plus 2nd/3rd harmonics (0.5, 0.3 amplitude) with an
  exponential decay. Assert the result is within ±2 cents. Also test with ±0.05 white noise
  (fixed seed), asserting within ±5 cents and no octave errors.
- Silence/noise-only input → detector returns null (or RMS gate rejects it).
- NoteMath: A4 = 440 → "A4", 0¢. 82.41 Hz → E2. A4 = 432 shift is respected.
- Tuning matcher: 100 Hz in Standard → targets A2 (110), not E2.
- PitchSmoother: an outlier in the median window is rejected.
