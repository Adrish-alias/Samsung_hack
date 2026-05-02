$ErrorActionPreference = "Stop"

$env:JAVA_HOME = "C:\Program Files\ojdkbuild\java-17-openjdk-17.0.3.0.6-1"
$env:ANDROID_SDK_ROOT = "C:\tmp\Android\sdk"
$env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_SDK_ROOT\platform-tools;$env:PATH"

Push-Location "$PSScriptRoot\..\android"
try {
    & "C:\tmp\gradle\gradle-8.10.2\bin\gradle.bat" :app:assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

Write-Host "APK built at android\app\build\outputs\apk\debug\app-debug.apk"
