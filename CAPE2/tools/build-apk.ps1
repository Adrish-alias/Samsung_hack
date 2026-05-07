$ErrorActionPreference = "Stop"

function Resolve-FirstExistingPath($candidates) {
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) {
            return (Resolve-Path $candidate).Path
        }
    }
    return $null
}

$javaHome = Resolve-FirstExistingPath @(
    "$PSScriptRoot\..\.jdks\jdk-17.0.19+10",
    $env:JAVA_HOME,
    "C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1",
    "C:\Program Files\Android\Android Studio\jbr",
    "C:\Program Files\JetBrains\PyCharm 2025.1.2\jbr"
)
if (-not $javaHome) {
    throw "JDK not found. Install Android Studio or set JAVA_HOME to JDK 17+."
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

$env:JAVA_HOME = $javaHome
$env:ANDROID_SDK_ROOT = $androidSdk
$env:ANDROID_HOME = $androidSdk
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"

Push-Location "$PSScriptRoot\..\android"
try {
    $gradleBat = Resolve-FirstExistingPath @(
        "C:\tmp\gradle\gradle-9.3.1\bin\gradle.bat",
        "$env:USERPROFILE\.gradle\wrapper\dists\gradle-9.3.1-bin\gradle-9.3.1\bin\gradle.bat"
    )
    if ($gradleBat) {
        & $gradleBat --no-daemon :app:assembleDebug
    } else {
        & "$env:JAVA_HOME\bin\java.exe" -classpath "gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain --no-daemon :app:assembleDebug
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host "APK built at android\app\build\outputs\apk\debug\app-debug.apk"
