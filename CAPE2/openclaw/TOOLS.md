# CAPE Tool Notes

- The Android APK applies packs; OpenClaw selects and explains them.
- The CAPE gateway receives Android context at `/v1/context/decision`.
- The gateway forwards orchestration requests to OpenClaw when configured.
- Keep JSON responses strict because the Android app parses them directly.
- Do not expose raw API keys, full location trails, or full calendar bodies.
