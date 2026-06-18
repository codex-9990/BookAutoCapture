# Development Status

Last updated: 2026-06-18

## Current Snapshot

BookAutoCapture is an Android/Kotlin app for fixed-position book capture. The current build focuses on reliable image capture first: CameraX preview, high-quality JPEG saving, lightweight page-turn detection, and a simple single-activity Compose UI.

## Implemented

- CameraX preview with rear camera
- CameraX `ImageCapture` JPEG saving to MediaStore
- Session folders under `Pictures/BookAutoCapture/YYYYMMdd_HHmmss/`
- Sequential filenames such as `page_0001.jpg`
- In-session saved page list with recent filenames
- Delete-last-capture action for the current session
- Pause/resume flow for the current capture session
- Restored session folder, recent page list, and next page number after app restart
- New-session action for starting another book without deleting old images
- CameraX `ImageAnalysis` using lightweight luma-frame sampling
- Auto-capture state machine:
  - `IDLE`
  - `WAITING_FOR_PAGE_TURN`
  - `PAGE_MOVING`
  - `WAITING_FOR_STABLE`
  - `CAPTURING`
  - `COOLDOWN`
- Stable-duration and minimum-capture-interval settings
- Sensitivity setting: low, medium, high
- Simplified control panel with one primary start/pause/resume action
- Photo orientation selector with landscape as the default and portrait as an option
- Live quality panel for brightness, blur, and stability before capture
- Optional blur and darkness checks
- Capture-complete sound toggle
- Notice that system shutter sound may still play depending on device policy
- Landscape layout with preview on the left and controls on the right
- Portrait layout with preview above and controls below
- Screen-awake mode while auto capture is running
- Startup cleanup of `cacheDir/tmp`
- Unit tests for the auto-capture state machine and quality assessment
- README with build, device-test, save-path, and limitation notes

## Verified Commands

Run locally from the repository root.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Both commands passed on 2026-06-18.

## Generated APK

Local debug build output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Build outputs are intentionally ignored by Git. GitHub Actions can upload a debug APK artifact for each CI run.

## Known Issues

- Real-device behavior has not been fully tuned across multiple Android vendors.
- Quality, blur, and darkness thresholds are simple luma-based heuristics and will need real capture samples.
- The UI is functional, but it still needs real-device polish on small phones and tablets.
- Restored session history depends on the MediaStore URIs recorded by the app. If files are changed externally, the list may become stale.
- No OCR, PDF export, page review screen, or session gallery exists yet.
- No release signing setup exists yet.

## Privacy Baseline

- Captured images stay on the device.
- The app does not upload images, OCR text, logs, or analytics.
- GitHub should store only source code, development notes, CI reports, and copyright-safe test fixtures.
- Do not commit captured book images, OCR text from books, signing keys, API keys, or `local.properties`.

## Next Recommended Check

Use an actual Android device and verify:

1. Portrait preview and manual capture.
2. Landscape preview and manual capture.
3. Auto capture after a page turn.
4. Screen stays awake while auto capture is running.
5. Saved image orientation is correct in the gallery.
6. Landscape photo orientation is the default.
7. Portrait photo orientation can be selected and saved correctly.
8. Quality panel changes between good, caution, and blocked in realistic lighting and motion.
9. No UI panel overlaps the camera preview while scrolling detailed settings.
10. The saved page list updates after each capture.
11. `中断` pauses auto capture and shows `再開`.
12. After closing and reopening the app, `再開` continues in the same folder with the next page number.
13. `新規開始` starts another session from `page_0001.jpg`.
14. `最後を削除` removes the newest saved image after auto capture is paused.
