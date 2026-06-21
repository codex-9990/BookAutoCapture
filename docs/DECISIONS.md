# Design Decisions

This file records why the project is shaped the way it is. Keep entries short so future changes have context.

## 2026-06-09: Local-First Capture App

Decision:
Keep image capture and processing local to the Android device.

Why:
The app is intended for fixed-position book capture and may handle sensitive or copyrighted material. Local-first behavior avoids accidental upload, account requirements, analytics, and backend maintenance.

Consequence:
GitHub should store source code, development notes, CI reports, and safe test fixtures only. Do not commit real captured book pages or OCR text from books.

## 2026-06-09: CameraX Without OpenCV

Decision:
Use CameraX Preview, ImageCapture, and ImageAnalysis. Avoid OpenCV for the MVP.

Why:
The initial requirement is page-turn detection, not full document correction. Luma-plane frame differencing is light enough for a first version and keeps dependencies small.

Consequence:
The app does not currently do perspective correction, curved-page correction, page splitting, or advanced blur detection.

## 2026-06-09: State Machine for Auto Capture

Decision:
Put auto-capture behavior in a plain Kotlin state machine.

Why:
The state transitions are easier to test outside Android. This reduces the chance of subtle regressions when UI or CameraX code changes.

Consequence:
Unit tests cover capture timing, cooldown, darkness, and blur-blocking decisions.

## 2026-06-09: MediaStore Direct Save

Decision:
Save final JPEGs directly through MediaStore into a public Pictures subfolder.

Why:
The app should not accumulate private image caches. The user should see files in standard Android gallery/file tools.

Consequence:
No storage permission is required on Android 10+ with the current minSdk 29 baseline.

## 2026-06-09: GitHub as Development Memory

Decision:
Use GitHub for source, docs, CI, and development history, not for user content.

Why:
Repository docs are useful as long-term project memory. GitHub Actions can verify builds and tests, but it is not a private document-processing backend.

Consequence:
Add and maintain `docs/STATUS.md`, `docs/DEVLOG.md`, `docs/ROADMAP.md`, and `docs/DECISIONS.md`.

## 2026-06-14: Lightweight Session History

Decision:
Track only filenames and MediaStore URIs for pages captured during the current app session.

Why:
Users need enough feedback to catch mistakes and remove the latest bad shot, but the app should not keep thumbnails or duplicate image caches.

Consequence:
The UI can show recent saved pages, delete the newest image, and recover the current session after app restart. External file changes may still make the stored list stale.

## 2026-06-14: One Primary Capture Action

Decision:
Make the control panel center on a single large start/stop button, with manual capture and delete as secondary actions.

Why:
The app is used while a phone is fixed above a book, so the main action should be obvious at a glance and easy to hit. Secondary controls should remain available without competing visually.

Consequence:
Manual capture is disabled during auto capture. Users stop auto capture before deleting the latest page or taking a manual replacement shot.

## 2026-06-14: Lightweight Quality Feedback Before OCR

Decision:
Use the existing luma analysis to show live brightness, blur, and stability quality before adding OCR or AI features.

Why:
The most useful next polish is preventing bad captures before they happen. Reusing the existing frame metrics keeps the app fast, local-first, and dependency-light.

Consequence:
The app now gives simple `良好` / `注意` / `撮影しません` feedback. Thresholds still need real-device tuning before treating the result as a final document-quality score.

## 2026-06-15: Landscape Photo Default

Decision:
Default saved photos to landscape orientation, with portrait available from the control panel.

Why:
An open book is usually wider than it is tall. Landscape output better matches the main capture case and reduces rotation work before OCR.

Consequence:
The app separates the saved photo orientation from the current screen layout. Real devices should still be checked for EXIF orientation behavior in gallery and OCR tools.

## 2026-06-18: Persist Resumable Session Metadata

Decision:
Persist the current capture session folder and captured page metadata in app preferences.

Why:
Book capture can be interrupted by app closure, battery issues, or a user break. Saving only lightweight metadata lets the app continue from the same folder without copying images or storing book content in GitHub or app-private caches.

Consequence:
The next capture continues from the restored page count. If files are deleted or edited outside the app, the stored MediaStore URI list may need future reconciliation.

## 2026-06-18: Capture-First Control Panel

Decision:
Put current mode, next filename, saved count, primary action, and quality feedback before secondary controls.

Why:
The user operates the app while handling a physical book, so the screen should answer only the immediate questions first: what session am I in, what happens if I tap the big button, and will the next photo be acceptable.

Consequence:
Secondary actions remain available, but manual capture, delete-last, new-book, and settings are grouped together below the main capture flow.

## 2026-06-21: Confirm Destructive Session Actions

Decision:
Ask for confirmation before deleting the latest photo or starting a new book.

Why:
Both actions can surprise users during repetitive capture work. A lightweight confirmation protects against accidental taps without adding a complex undo system.

Consequence:
The app is safer for first-time users. A true undo flow remains a future improvement if real-device use shows deletion mistakes are common.

## 2026-06-21: Match Preview to Photo Orientation

Decision:
Rotate and frame the live preview according to the selected photo orientation.

Why:
Users expect the camera view to match the image that will be saved. Showing a portrait preview while saving landscape photos makes framing an open book confusing.

Consequence:
The preview now letterboxes into a landscape or portrait frame. Device-specific CameraX behavior should still be checked on real hardware.
