$ErrorActionPreference = "Stop"

$env:ANDROID_SDK_ROOT = "C:\tmp\Android\sdk"
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:USERPROFILE = "C:\Users\adris"
$env:PATH = "$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"

$apk = Join-Path $PSScriptRoot "..\android\app\build\outputs\apk\debug\app-debug.apk"

adb devices
adb reverse tcp:8787 tcp:8787
adb install -r $apk
adb shell monkey -p dev.rootcause.cape 1
