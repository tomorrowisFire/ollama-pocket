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
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_NDK:-}}}"
if [[ -z "$NDK" ]]; then
  echo "Set ANDROID_NDK_HOME to compile the CPU/GPU/NPU diagnostic probe" >&2
  exit 1
fi
HOST="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64"
CC="$NDK/toolchains/llvm/prebuilt/$HOST/bin/aarch64-linux-android26-clang"
"$CC" -fPIC -O2 -shared -Wl,-z,max-page-size=16384 \
  -o "$OLLAMA_ANDROID_JNI_LIBS_DIR/arm64-v8a/libdeviceprobe.so" \
  "$ROOT/app/src/main/cpp/deviceprobe.c" -ldl
echo "Ready: $ROOT/app/src/main/jniLibs/arm64-v8a/{libollama.so,libllama-server.so,libdeviceprobe.so}"
