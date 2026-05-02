$ErrorActionPreference = "Stop"

$env:CAPE_GATEWAY_HOST = "127.0.0.1"
$env:CAPE_GATEWAY_PORT = "8787"
$env:OLLAMA_MODEL = "gemma4:latest"
$env:OLLAMA_TIMEOUT_MS = "30000"
$env:OPENCLAW_CAPE_MEMORY_DIR = "$PSScriptRoot\..\openclaw\runtime"

npm.cmd run gateway:start
