# Development Log

This file is the chronological memory of the project. Keep entries short, concrete, and tied to verified behavior.

## 2026-06-21

### Preview Orientation Match

- Made the CameraX preview target rotation follow the selected photo orientation.
- Added a landscape or portrait preview frame so the camera view matches `写真の向き`.
- Kept the saved JPEG orientation and visible preview in sync when switching between `横長` and `縦長`.

## 2026-06-18

### Resumable Capture Sessions

- Changed the running primary action from stop to `中断`.
- Added resume behavior so a paused session continues in the same folder.
- Persisted the current session folder and captured page list in app preferences.
- Restored the captured page count, last filename, and next page number after app restart.
- Added `新しい本` for beginning another book without deleting old captured images.

### Capture UI Simplification

- Reordered the control panel around current mode, primary action, next filename, and quality feedback.
- Renamed the primary actions to `撮影を開始`, `中断する`, and `続きから撮る`.
- Grouped manual capture, delete-last, new-book, and settings actions under one `操作` section.
- Moved the shutter-sound notice into detailed settings to reduce visual noise during capture.
- Added confirmation dialogs before deleting the latest photo or starting a new book.

## 2026-06-15

### Photo Orientation

- Added a visible `写真の向き` selector to the control panel.
- Made landscape the default save orientation for open books.
- Kept portrait available as a one-tap option.
- Persisted the selected photo orientation in app preferences.

## 2026-06-14

### Capture Session Polish

- Added an in-session saved page list showing recent filenames.
- Added `最後を削除` so the latest saved image can be removed after stopping auto capture.
- Kept the session list lightweight by storing filenames and MediaStore URIs only.
- Replaced deprecated `ImageAnalysis.Builder.setTargetResolution` usage with `ResolutionSelector`.
- Updated README and status docs with the new test steps and known limitation.

### Simple Control Panel

- Reworked the control panel around one large primary start/stop button.
- Moved state and capture count into a single status summary.
- Grouped save path, last filename, and recent saved pages into one session section.
- Disabled manual capture while auto capture is running to reduce accidental double captures.

### Quality Check Polish

- Added a lightweight quality assessment model for brightness, blur, and stability.
- Added a live quality panel showing `良好`, `注意`, or `撮影しません` before capture.
- Shared darkness and blur thresholds between auto-capture blocking and quality display.
- Added unit tests for good, caution, blocked, moving, and disabled-check cases.

### Verification

Passed:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

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
