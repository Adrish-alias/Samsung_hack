$ErrorActionPreference = "Stop"

$env:CAPE_GATEWAY_HOST = "127.0.0.1"
$env:CAPE_GATEWAY_PORT = "8787"
$env:OLLAMA_MODEL = "gemma4:latest"
$env:OLLAMA_TIMEOUT_MS = "30000"
$env:OPENCLAW_CAPE_MEMORY_DIR = "$PSScriptRoot\..\openclaw\runtime"

$envFile = Join-Path $PSScriptRoot "..\.env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), "Process")
        }
    }
}

npm.cmd run gateway:start
