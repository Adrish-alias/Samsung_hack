#!/bin/bash

set -e

echo "Building CAPE Android APK..."

cd "$(dirname "$0")/../android"

# Check if Gradle wrapper exists
if [ ! -f "./gradlew" ]; then
    echo "Error: Gradle wrapper not found. Please ensure the Android project is properly set up."
    exit 1
fi

# Make gradlew executable
chmod +x ./gradlew

# Build the APK
echo "Running Gradle build..."
./gradlew assembleDebug

APK_PATH="./app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "✅ APK built successfully: $APK_PATH"
    echo "Install with: ./tools/install-apk.sh"
else
    echo "❌ APK build failed"
    exit 1
fi
