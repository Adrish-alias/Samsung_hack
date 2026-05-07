# CAPE Project Report

## Current Build Summary

CAPE is now an Android-first context-aware automation system with a local CAPE/OpenClaw gateway. The phone senses user context, sends it to `/v1/context/decision`, receives a pack decision, and applies device behavior changes through Android APIs. The gateway preserves the OpenClaw-style multi-agent pipeline and writes durable memory/session artifacts under `openclaw/`.

## Implemented Features

### Android APK

- Kotlin/Jetpack Compose APK builds and installs successfully.
- Onboarding captures user name and optional home/work/college places.
- Runtime permission flow covers location, calendar, notifications, DND access, write settings, and usage access.
- Foreground service keeps periodic context sync active.
- USB gateway bridge is supported through `adb reverse tcp:8787 tcp:8787`.
- `tools/keep-phone-synced.ps1` keeps the gateway and USB bridge alive while a phone is connected.
- Dashboard shows stress score, context, permissions, pack decision, OpenClaw runtime metadata, and agent trace.
- Commute tab shows ETA, route source, route directions, Google Maps button, and feedback controls.
- Profile tab saves location places through gateway geocoding.
- Profile tab stores the user's role (`student`/`employee`) and daily routine timing.
- Plan tab generates a daily routine/meeting checklist, asks the user to confirm true items, and adds approved blocks to Android Calendar.
- Reflection bottom sheet captures daily reflection tags and optional notes.

### Context Sensing

- Calendar sensing reads only pending/current upcoming events and avoids completed meetings.
- Confirmed CAPE routine blocks are written back to Calendar with 10-minute reminders, so the sensing loop can prepare focus mode before college/work/meetings.
- Location sensing classifies saved places into `home`, `office`, `college`, `relaxing`, or `commuting`.
- Fixed saved places now carry an arrival radius, and CAPE logs the nearest saved place during sensing.
- Arrival at home/work/college/relaxing coordinates directly updates `locationState`, which drives automatic wallpaper and pack changes.
- Home, work, and college are independently editable and saved together, so changing one no longer wipes the others.
- Saved-place fields support gateway-backed Google place suggestions while typing, then save the selected GPS coordinates.
- UsageStats sensing reads screen time and foreground behavior.
- Behavioral stress signals are sent to the gateway:
  - `appSwitchCountLast30Min`
  - `screenUnlockCountLast30Min`
  - `notificationCountLast30Min`
  - `foregroundAppCategory`
  - `timeAtLocationMinutes`
  - `implicitWorkload`
- Implicit workload becomes `HIGH` when the user stays at office/college for more than 120 minutes with no calendar meetings.

### Automation Packs

- Four wallpaper themes are present and functional:
  - Focus
  - Relax
  - Commute
  - Reset/default
- Manual wallpaper demo still works.
- Dynamic wallpaper automation is now independent from the full pack permission path, so wallpaper can change even if DND/brightness actions are blocked.
- Location/decision mapping:
  - `commuting` -> commute theme and commute pack
  - `office`/`college` or meeting starting -> focus theme and focus pack
  - `home`/`relaxing` -> relax theme and relax pack
  - `observe_only` -> reset/default theme
- Auto-apply is enabled by default and still respects safety and permission blockers for non-wallpaper actions.

### Gateway And OpenClaw Pipeline

- Existing endpoints preserved:
  - `GET /health`
  - `POST /v1/context/decision`
  - `POST /v1/feedback`
  - `POST /v1/maps/geocode`
- Response schema preserved with `decision`, `agentTrace`, and `openclaw`.
- Local fallback pipeline implements:
  - Context Intake Agent
  - Routine Memory Agent
  - Stress Scoring Agent
  - Commute Agent
  - Decision Orchestrator Agent
  - Safety & Permission Agent
  - Pack Execution readiness Agent
  - Feedback Learning Agent
- Remote OpenClaw bridge hooks exist through `OPENCLAW_BASE_URL`, `OPENCLAW_TOKEN`, and `OPENCLAW_AGENT_ID`.
- Runtime logs and summaries are written under `openclaw/runtime`.
- YAML memory is stored under `openclaw/memory`.

### Stress And Learning

- Stress scoring no longer depends heavily on meetings.
- New scoring uses sleep debt, app switching, unlocks, screen time, notifications, commute pressure, and a small meeting component.
- `implicitWorkload == HIGH` increases stress.
- Mixed foreground app context adds stress pressure.
- Reflection feedback maps tags to adaptive stress adjustments:
  - Heavy workload: `+15`
  - Assignments: `+10`
  - Exams: `+20`
  - Personal stress: `+15`
  - Chill day: `-10`
- Reflection updates:
  - `user_profile.yaml`
  - `routine_patterns.yaml`
  - `SOUL.md`

### Commute

- Google Routes API support is present when `GOOGLE_MAPS_API_KEY` is configured.
- Fallback commute estimates work without Maps API.
- Commute UI shows route details and opens Google Maps directly.
- Departure alerts and late alerts are supported through Android notifications.

### Daily Routine Planning

- User can choose student or employee mode.
- User can store routine start/end times.
- CAPE generates today's remaining college/work block plus pending meeting items.
- Each item must be confirmed by the user before Calendar insertion.
- Added Calendar events include a 10-minute reminder.
- Existing pack automation now treats events within 10 minutes as focus-start context.

## Verified

- `npm test` passes.
- `npm run gateway:test` passes.
- Debug APK builds successfully.
- APK installs successfully on connected phone.
- Gateway health endpoint returns OK.
- USB reverse bridge is active when configured.

## Features And Dependencies Still Needed

### Ollama Reasoning Layer

- A lightweight Ollama reasoning-note client exists, but the reasoning layer is not yet a full decision authority.
- Current core decisions are still deterministic/local fallback decisions with optional reasoning notes.
- Needed:
  - Pull and configure a model, recommended `deepseek-r1:8b`.
  - Set `OLLAMA_BASE_URL=http://127.0.0.1:11434`.
  - Set `OLLAMA_MODEL=deepseek-r1:8b`.
  - Make reasoning output structured enough to influence decisions safely, not only explain them.
  - Add tests for Ollama unavailable, model missing, timeout, and malformed response.

### Full Remote OpenClaw Runtime

- Remote OpenClaw connection code exists, but a fully running upstream OpenClaw gateway with authenticated CAPE agent is still required for end-to-end remote orchestration.
- Needed:
  - Configure `OPENCLAW_BASE_URL`.
  - Configure `OPENCLAW_TOKEN`.
  - Configure `OPENCLAW_AGENT_ID=cape`.
  - Verify actual remote OpenClaw WebSocket responses against the CAPE JSON contract.

### Google Maps Production Setup

- Code supports Google Maps Routes and Geocoding, but production use depends on a valid API key.
- Needed:
  - Enable Geocoding API.
  - Enable Routes API.
  - Add a billing-enabled Google Cloud project.
  - Store `GOOGLE_MAPS_API_KEY` in `.env`.

### Android Background Robustness

- A foreground service exists, but Android OEM battery restrictions may still pause it.
- Needed:
  - Battery optimization exemption flow.
  - Boot receiver to restart sync after phone reboot.
  - More resilient location updates using fused location/geofencing instead of only last-known location.

### Notification Count Accuracy

- CAPE currently counts notifications it emits itself.
- True global notification counting needs an Android Notification Listener Service and user-granted notification listener access.

### App Category Accuracy

- App category detection uses Android app metadata and package-name fallback.
- Needed:
  - Better local category map for common Indian/student/work apps.
  - User override for app categories.

### Reflection Product Polish

- Reflection bottom sheet exists.
- Needed:
  - Better trigger deduplication across app restarts.
  - More detailed reflection history UI.
  - Stronger mapping between reflection trends and future pack confidence.

### Submission Polish

- Needed:
  - Demo script.
  - Screenshots or video of each mode.
  - Architecture diagram updated with behavioral stress and reflection loop.
  - Final README instructions for setup, gateway, Ollama, Maps, APK install, and USB bridge.
