#!/usr/bin/env sh
# Local development start: checks for a JDK and Ollama, then runs the app.
set -e
command -v javac >/dev/null || { echo "A JDK 21+ is required (javac not found)."; exit 1; }
if ! curl -fs "${OLLAMA_BASE_URL:-http://localhost:11434}/api/tags" >/dev/null; then
  echo "Warning: Ollama is not reachable. The compiler works; AI features will be unavailable."
  echo "Start it with: ollama serve   and   ollama pull ${OLLAMA_MODEL:-qwen2.5-coder:7b}"
fi
mvn spring-boot:run
