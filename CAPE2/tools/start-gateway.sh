#!/bin/bash

set -e

export CAPE_GATEWAY_HOST="127.0.0.1"
export CAPE_GATEWAY_PORT="8787"
export OLLAMA_MODEL="gemma4:latest"
export OLLAMA_TIMEOUT_MS="30000"
export OPENCLAW_CAPE_MEMORY_DIR="$(dirname "$0")/../openclaw/runtime"

# Load .env file if it exists
ENV_FILE="$(dirname "$0")/../.env"
if [ -f "$ENV_FILE" ]; then
    while IFS= read -r line || [[ -n "$line" ]]; do
        # Skip comments and empty lines
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ -z "$line" ]] && continue
        
        # Export environment variables
        if [[ "$line" == *"="* ]]; then
            export "$(echo "$line" | cut -d'=' -f1)"="$(echo "$line" | cut -d'=' -f2-)"
        fi
    done < "$ENV_FILE"
fi

npm run gateway:start
