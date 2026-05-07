# PixelShakeTorch

Android app: shake your phone to toggle the flashlight.

## Features

- Accelerometer-based shake detection (3 g-force peaks ≥ 2.7g within 1s, 1.5s cooldown)
- Toggles the back-camera torch via `CameraManager.setTorchMode`
- Manual on/off button as a fallback
- Short haptic confirmation on each shake-toggle
- Material 3 (DayNight) theme

## Requirements

- Android 7.0 (API 24) or newer
- Device with a back-camera flash

## Building

Open the project in Android Studio (Iguana or newer) and let it sync. Or, with the Android SDK installed and `gradle` on PATH:

```sh
gradle assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## CI

Every push triggers `.github/workflows/build.yml`, which builds the debug APK and uploads it as a workflow artifact named `pixelshaketorch-debug-apk`.
