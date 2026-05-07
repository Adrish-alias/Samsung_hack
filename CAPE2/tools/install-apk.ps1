$ErrorActionPreference = "Stop"

function Resolve-FirstExistingPath($candidates) {
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

$androidSdk = Resolve-FirstExistingPath @(
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    "$PSScriptRoot\..\.android-sdk",
    "$env:LOCALAPPDATA\Android\Sdk",
    "C:\Android\Sdk",
    "C:\tmp\Android\sdk"
)
if (-not $androidSdk) {
    throw "Android SDK not found. Install Android Studio SDK tools or set ANDROID_SDK_ROOT."
}

$env:ANDROID_SDK_ROOT = $androidSdk
$env:ANDROID_HOME = $androidSdk
$env:PATH = "$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"

$apk = Join-Path $PSScriptRoot "..\android\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) {
    throw "APK not found at $apk. Run tools\build-apk.ps1 first."
}

adb devices
adb reverse tcp:8787 tcp:8787
adb install -r $apk
adb shell monkey -p dev.rootcause.cape 1

$syncWatcher = Join-Path $PSScriptRoot "keep-phone-synced.ps1"
Start-Process -FilePath powershell -ArgumentList @("-ExecutionPolicy", "Bypass", "-File", $syncWatcher) -WorkingDirectory (Resolve-Path "$PSScriptRoot\..").Path -WindowStyle Hidden | Out-Null
Write-Host "CAPE phone sync watcher started. It keeps the gateway and adb reverse active while USB is connected."
