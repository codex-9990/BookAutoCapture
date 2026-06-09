# Roadmap

This roadmap is intentionally practical. The app should become a dependable capture tool before adding heavier document or AI features.

## Phase 1: Capture Reliability

- Tune page-turn sensitivity on real devices.
- Replace deprecated analyzer target-resolution API.
- Add a session review screen.
- Add delete/retry for the last captured page.
- Add clearer camera permission recovery UI.
- Add visible warning when storage save fails.
- Add tablet layout checks.

## Phase 2: Document Workflow

- Add session list.
- Add page thumbnail review without keeping unnecessary app-private image caches.
- Add export of a simple session manifest:

```json
{
  "session": "YYYYMMdd_HHmmss",
  "pages": ["page_0001.jpg"]
}
```

- Add optional page renumbering after deletion.
- Add manual focus/exposure controls only if real device testing proves they are needed.

## Phase 3: OCR

- Add optional on-device OCR using ML Kit Text Recognition.
- Keep OCR output local by default.
- Save plain text per page or per session only when the user enables it.
- Add a review/edit screen for OCR text.
- Add clear README notes about privacy and copyright-safe use.

## Phase 4: Lightweight AI Experiments

- Keep AI optional and local-first.
- Prefer OCR first, then use a small model for text cleanup, summaries, and keyword extraction.
- Use GitHub Actions only for copyright-safe test fixtures, prompt experiments, and quality reports.
- Do not upload real user book images or OCR text to GitHub.

## Not Planned for MVP

- Cloud upload
- Login
- Ads
- Billing
- DRM bypass
- Root or ADB workflows
- Large persistent app caches
- Analytics SDKs

