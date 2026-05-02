# CAPE

Context-Aware Policy Engine for Samsung PRISM OpenClaw Hackathon.

CAPE is an Android-first daily utility agent that senses calendar, location, usage,
sleep proxy, and commute pressure, then coordinates OpenClaw agents to apply the
right phone behavior pack at the right time.

## Current Build Slice

This first slice contains:

- `packages/cape-core`: testable JavaScript reference implementation of stress scoring and pack decisions.
- `openclaw`: starter memory, rules, and `SKILL.md` behavior packs.
- `android`: Kotlin/Jetpack Compose project skeleton for the APK.
- `docs`: production architecture notes.

## Test The First Slice

```powershell
npm test
```

Expected result:

```text
All CAPE core tests passed
```

## Build The Demo APK

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\build-apk.ps1
```

Output:

```text
android\app\build\outputs\apk\debug\app-debug.apk
```

To install and launch on a connected USB-debugging Android phone:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\install-apk.ps1
```

To let the APK ask the local CAPE/OpenClaw gateway for decisions, keep this
running in another terminal:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\start-gateway.ps1
```

If you want the gateway to use the WSL Ollama/OpenClaw setup directly:

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\start-gateway-wsl.ps1
```

The install script runs `adb reverse tcp:8787 tcp:8787`, so the phone can call
the gateway at `http://127.0.0.1:8787` over USB.

Inside the app, test:

- Choose Office, Commute, Recovery, or Home.
- Grant permissions from the permission panel.
- Tap Apply Pack.
- Confirm the decision, stress score, blocked permissions, and feedback state update.

Gateway/OpenClaw-readable memory is written to:

```text
openclaw\runtime\latest-context.json
openclaw\runtime\latest-summary.md
openclaw\runtime\events.jsonl
```

## OpenClaw Model

Local OpenClaw is configured to use:

```text
ollama/llama3.1:8b
```

Keep secrets out of Git. Use `.env.example` as the template for local values.
