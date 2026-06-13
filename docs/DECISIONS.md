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
The current UI can show recent saved pages and delete the newest image. Full session recovery after app restart remains a future feature.
