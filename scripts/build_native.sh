#!/usr/bin/env bash
# Local build: Linux/macOS with NDK, CMake>=3.24, Go, git. Uses open-source Ollama ARM64 fork.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_ROOT="${RUNTIME_ROOT:-$ROOT/runtime}"
if [[ ! -d "$RUNTIME_ROOT/.git" ]]; then
  git clone --depth 1 https://github.com/RadekParek/Rllama.git "$RUNTIME_ROOT"
fi
export OLLAMA_ANDROID_JNI_LIBS_DIR="$ROOT/app/src/main/jniLibs"
(
  cd "$RUNTIME_ROOT"
  bash ./scripts/build_android.sh -a arm64-v8a
)
echo "Ready: $ROOT/app/src/main/jniLibs/arm64-v8a/{libollama.so,libllama-server.so}"
