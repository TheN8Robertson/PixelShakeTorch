# PixelShakeTorch — Build Session Log

A record of how this app came together: the architecture, the version-by-version progression, and the gotchas worth remembering for next time.

- Repo: https://github.com/TheN8Robertson/PixelShakeTorch (public)
- Latest: v0.6.0
- Distribution: GitHub Releases → Obtainium

## What it is

Android shake-to-flashlight app. Tuneable sensitivity, runs in the background via a foreground service so the screen can be off and the app closed. Material 3 single-activity UI, Kotlin, minSdk 24, targetSdk 34.

## File layout

```
PixelShakeTorch/
├── app/
│   ├── build.gradle.kts          # AGP 8.5.2, view binding, debug-signed
│   ├── debug.keystore            # Stable signing key (committed; debug only)
│   └── src/main/
│       ├── AndroidManifest.xml   # Perms + service decl with specialUse FGS
│       ├── java/.../pixelshaketorch/
│       │   ├── MainActivity.kt           # UI, slider, switch, perm flow
│       │   ├── ShakeService.kt           # Foreground service
│       │   ├── ShakeDetector.kt          # SensorEventListener
│       │   ├── FlashlightController.kt   # CameraManager.setTorchMode wrapper
│       │   └── Prefs.kt                  # SharedPreferences keys/defaults
│       └── res/                  # layout, strings, themes, ic_notif_bolt
├── .github/workflows/build.yml   # CI: setup-java/setup-android/setup-gradle, assembleDebug
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml     # Version catalog (Kotlin 2.0.20)
└── gradle.properties
```

No Gradle wrapper is committed — CI generates it on the fly via `gradle wrapper --gradle-version 8.7`. Local dev opens in Android Studio which generates its own.

## Version history

| Version | What | Why |
|---|---|---|
| v0.1.0 | Initial: shake → toggle in foreground only | Get the loop working: accelerometer → 3 peaks ≥ 2.7g/1s → CameraManager.setTorchMode |
| v0.2.0 | Foreground service + background mode toggle | App-only listening was the bare minimum; users want it to work with the screen off |
| v0.3.0 | Background mode default-on at first launch + drop CAMERA perm | Auto-enable removes a setup step. CAMERA permission was implied by the camera FGS type, not the flashlight API itself — switching FGS type to `specialUse` lets us drop CAMERA entirely |
| v0.4.0 | Commit stable debug.keystore | CI runners generate ephemeral debug keys, so each release was signed with a different key — Obtainium reported `Conflict` on upgrade. Stable committed keystore fixes upgrades forever |
| v0.5.0 | Sensitivity slider (1.5g–4.5g, 0.1g step) | Different users want different shake strengths. Threshold is mutable + persisted; service picks up changes via `OnSharedPreferenceChangeListener` without restarting |
| v0.6.0 | Slider max 4.5g → 8.0g | 4.5g triggered too easily; needed headroom for users who want to suppress accidental shakes |

## Lessons worth keeping

### 1. `setTorchMode` does not require `CAMERA` permission

Only `CameraManager.openCamera` does. The reason CAMERA appeared in v0.1.0–v0.2.0 was that the foreground service used `foregroundServiceType="camera"`, which *does* require it. Switching the service to `foregroundServiceType="specialUse"` (the right category for sensor-driven background work) lets the app function with no CAMERA permission at all — much friendlier install dialog.

### 2. CI debug builds without a stable keystore = upgrade Conflict

Each GitHub Actions runner generates a fresh `~/.android/debug.keystore`. Two builds = two different signing keys = Android refuses the upgrade. **Always commit a stable debug keystore for sideload distribution**, even if it's just the standard `androiddebugkey` / `android` / `android` triplet. One-time pain to migrate (users uninstall once) but every future upgrade just works.

### 3. Wake lock is mandatory for sensor delivery with screen off

A foreground service alone doesn't keep the CPU awake. Without `PowerManager.PARTIAL_WAKE_LOCK`, the accelerometer stops delivering events the moment the device sleeps, even though the FGS notification stays up. `WAKE_LOCK` permission + a held partial wake lock are required.

### 4. Android FGS type matters more on API 34+

Pre-API 34: type largely advisory.
API 34+: must declare a type, must hold the matching `FOREGROUND_SERVICE_<TYPE>` permission, must call the 3-arg `startForeground(id, notification, type)`. `specialUse` also needs a `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>` inside the service tag.

### 5. Material Slider listener fires on programmatic state too

When restoring switch/slider state in lifecycle callbacks (e.g., reflecting `ShakeService.isRunning` in `onStart`), use a "set silently" helper that detaches the listener around the assignment. Otherwise lifecycle events trigger user-action handlers.

## Build & release runbook

### CI
Push to `main` → `.github/workflows/build.yml` runs on `ubuntu-latest`:
1. `setup-java@v4` (Temurin 17)
2. `setup-android@v3`
3. `setup-gradle@v4` (gradle-version 8.7)
4. `gradle wrapper --gradle-version 8.7`
5. `./gradlew assembleDebug --stacktrace`
6. Upload `app/build/outputs/apk/debug/*.apk` as `pixelshaketorch-debug-apk`

### Cutting a release
Manual for now (not auto-tagged on push):
1. Bump `versionCode` + `versionName` in `app/build.gradle.kts`
2. Commit + push `main`
3. Watch CI build go green
4. `gh run download <id> -n pixelshaketorch-debug-apk -D artifacts/<version>`
5. Rename `app-debug.apk` → `pixelshaketorch-<version>.apk`
6. `git tag <version>` + push tag
7. `gh api repos/.../releases -X POST` to create the release with notes
8. `gh release upload <version> <apk>`

Notes:
- gh CLI on this machine has two accounts (`TheN8Robertson` and `TheN8Robertson-AF`). The active one drifts back to `TheN8Robertson-AF` between sessions; pass `GH_TOKEN=$(gh auth token -u TheN8Robertson)` explicitly when acting on the repo.
- Pushes use a token-in-URL workaround because the system credential helper caches the wrong account: `git push https://TheN8Robertson:${TOKEN}@github.com/TheN8Robertson/PixelShakeTorch.git`.
- `gh release create` sometimes errors with "workflow scope may be required" even when the token has the scope. Workaround: create the release via `gh api repos/.../releases -X POST` instead.

### Obtainium setup
Source URL: `https://github.com/TheN8Robertson/PixelShakeTorch`. It picks the latest release with an `.apk` asset. APK naming `pixelshaketorch-vX.Y.Z.apk` is regex-friendly; no custom regex required.

## Permission set (final)

- `VIBRATE` — short haptic on each shake-toggle
- `WAKE_LOCK` — hold CPU awake while listening for shakes
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — required for the background service on API 34+
- `POST_NOTIFICATIONS` — required for the FGS notification on API 33+

No `CAMERA`, no `INTERNET`, no `RECEIVE_BOOT_COMPLETED`, no analytics SDK.

## Possible future work

- **Auto-restart at boot**: add a `BOOT_COMPLETED` receiver so the service comes back without opening the app after reboot. Costs a permission and a class.
- **Configurable peak count / window**: expose `minPeaks` and `peakWindowMs` from `ShakeDetector` if threshold alone isn't enough discrimination.
- **Auto-release on push**: extend `build.yml` to publish a release on tag push (cleaner than the manual `gh api` dance).
- **Adaptive launcher icon**: currently uses the system fallback. A custom adaptive icon would feel less "scaffolded."
- **Battery optimization exemption nudge**: doze mode can still kill the wake lock on aggressive OEMs (Samsung, Xiaomi). A one-time prompt to whitelist the app via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` would harden the background path.
