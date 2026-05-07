$ErrorActionPreference = "Stop"

function Resolve-FirstExistingPath($candidates) {
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$androidSdk = Resolve-FirstExistingPath @(
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    "$repoRoot\.android-sdk",
    "$env:LOCALAPPDATA\Android\Sdk",
    "C:\Android\Sdk",
    "C:\tmp\Android\sdk"
)
if (-not $androidSdk) {
    throw "Android SDK not found. Install Android Studio SDK tools or set ANDROID_SDK_ROOT."
}

$adb = Join-Path $androidSdk "platform-tools\adb.exe"
$gatewayScript = Join-Path $PSScriptRoot "start-gateway.ps1"
$logDir = Join-Path $repoRoot "openclaw\runtime"
$logFile = Join-Path $logDir "phone-sync.log"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Write-SyncLog($message) {
    $line = "$(Get-Date -Format o) $message"
    Add-Content -Path $logFile -Value $line
    Write-Host $line
}

function Get-GatewayListener {
    Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
}

function Ensure-Gateway {
    if (Get-GatewayListener) {
        return
    }
    Write-SyncLog "Starting CAPE gateway on port 8787"
    Start-Process -FilePath powershell -ArgumentList @("-ExecutionPolicy", "Bypass", "-File", $gatewayScript) -WorkingDirectory $repoRoot -WindowStyle Hidden | Out-Null
    Start-Sleep -Seconds 3
}

function Has-Device {
    $devices = & $adb devices
    return [bool]($devices | Select-String -Pattern "`tdevice$")
}

Write-SyncLog "CAPE phone sync watcher started"

while ($true) {
    try {
        Ensure-Gateway
        if (Has-Device) {
            & $adb reverse tcp:8787 tcp:8787 | Out-Null
            Write-SyncLog "USB bridge active: phone 127.0.0.1:8787 -> laptop 127.0.0.1:8787"
        } else {
            Write-SyncLog "No authorized USB device found"
        }
    } catch {
        Write-SyncLog "Sync watcher error: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds 10
}
