# Development Log

This file is the chronological memory of the project. Keep entries short, concrete, and tied to verified behavior.

## 2026-06-09

### Initial Android Project

- Created a new Android Studio project for `BookAutoCapture`.
- Added Kotlin, Jetpack Compose, CameraX, and Gradle wrapper setup.
- Set `minSdk` to 29 and `compileSdk` / `targetSdk` to 36 for the local Android SDK.
- Implemented a single-activity architecture.

### Camera and Saving

- Added CameraX preview with rear camera.
- Added high-quality JPEG capture with `ImageCapture`.
- Saved images through MediaStore to `Pictures/BookAutoCapture/YYYYMMdd_HHmmss/`.
- Added sequential page filenames.
- Added app startup cleanup for `cacheDir/tmp`.

### Auto Capture

- Added luma-plane frame sampling for lightweight motion analysis.
- Added auto-capture state machine.
- Added configurable stable wait time, minimum capture interval, sensitivity, blur check, and darkness check.
- Added unit tests for capture request, insufficient stability, cooldown, darkness blocking, and blur blocking.

### UI Improvements

- Added Compose UI with preview, start/stop/manual capture controls, capture count, state display, save path, last filename, sound toggle, and detailed settings.
- Added app-side capture-complete sound toggle.
- Added warning that device/system shutter sound may still play.
- Added landscape support.
- Added screen-awake behavior during auto capture.
- Removed preview overlays so settings scroll does not cover the camera area.
- Changed preview scaling to `FIT_CENTER` to preserve the book framing.

### Verification

Passed:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## Entry Template

```md
## YYYY-MM-DD

### Topic

- What changed:
- Why:
- Verified:
- Follow-up:
```

