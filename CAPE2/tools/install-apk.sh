#!/bin/bash

set -e

# Find Android SDK
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$ANDROID_SDK_ROOT" ]; then
    # Common Android SDK locations on macOS
    CANDIDATES=(
        "$HOME/Library/Android/sdk"
        "$HOME/Android/Sdk"
        "/usr/local/Android/Sdk"
        "/opt/Android/Sdk"
    )
    
    for candidate in "${CANDIDATES[@]}"; do
        if [ -d "$candidate" ]; then
            ANDROID_SDK_ROOT="$candidate"
            break
        fi
    done
    
    if [ -z "$ANDROID_SDK_ROOT" ]; then
        echo "Error: Android SDK not found. Please set ANDROID_SDK_ROOT or install Android Studio."
        exit 1
    fi
fi

export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/platform-tools:$PATH"

APK_PATH="$(dirname "$0")/../android/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK not found at $APK_PATH. Run tools/build-apk.sh first."
    exit 1
fi

echo "Checking connected devices..."
adb devices

echo "Setting up USB reverse port forwarding..."
adb reverse tcp:8787 tcp:8787

echo "Installing APK..."
adb install -r "$APK_PATH"

echo "Launching app..."
adb shell monkey -p dev.rootcause.cape 1

echo "CAPE phone setup complete!"
echo "Make sure the gateway is running: ./tools/start-gateway.sh"
