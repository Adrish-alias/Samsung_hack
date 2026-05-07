$ErrorActionPreference = "Stop"

wsl sh -lc "cd '/mnt/c/Users/adris/Documents/New project' && CAPE_GATEWAY_HOST=0.0.0.0 CAPE_GATEWAY_PORT=8787 OLLAMA_BASE_URL=http://127.0.0.1:11434 OLLAMA_MODEL=llama3.1:8b OLLAMA_TIMEOUT_MS=45000 OPENCLAW_CAPE_MEMORY_DIR='/mnt/c/Users/adris/Documents/New project/openclaw/runtime' npm run gateway:start"
