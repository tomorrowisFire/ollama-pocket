#!/usr/bin/env bash
# Optional Android Vulkan llama.cpp runtime.
# The CPU Ollama path remains fully functional if this build fails.
# LGPL? No: llama.cpp is MIT licensed; licenses included in APK assets separately.
set -euxo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
: "${ANDROID_NDK_HOME:?ANDROID_NDK_HOME required}"
NDK="$ANDROID_NDK_HOME"
TARGET="$ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$TARGET"
rm -f "$TARGET/libllama-vulkan-server.so"
SRC="$ROOT/vulkan-build/llama.cpp"
if [ ! -d "$SRC/.git" ]; then
  mkdir -p "$ROOT/vulkan-build"
  git clone --depth 1 --branch b11913 https://github.com/ggml-org/llama.cpp.git "$SRC"
fi
mkdir -p "$ROOT/vulkan-build/headers"
for dep in Vulkan-Headers SPIRV-Headers; do
  if [ ! -d "$ROOT/vulkan-build/headers/$dep/include" ]; then
    git clone --depth 1 "https://github.com/KhronosGroup/$dep.git" "$ROOT/vulkan-build/headers/$dep"
  fi
done
HDR="-I$ROOT/vulkan-build/headers/Vulkan-Headers/include -I$ROOT/vulkan-build/headers/SPIRV-Headers/include"
cmake -S "$SRC" -B "$ROOT/vulkan-build/build" -G Ninja \
 -DCMAKE_TOOLCHAIN_FILE="$ROOT/scripts/android_vulkan_toolchain.cmake" \
 -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
 -DCMAKE_BUILD_TYPE=Release \
 -DCMAKE_C_FLAGS="$HDR" -DCMAKE_CXX_FLAGS="$HDR" \
 -DVulkan_INCLUDE_DIR="$ROOT/vulkan-build/headers/Vulkan-Headers/include" \
 -DGGML_VULKAN=ON -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF \
 -DGGML_BACKEND_DL=OFF -DBUILD_SHARED_LIBS=OFF \
 -DLLAMA_BUILD_SERVER=ON -DLLAMA_CURL=OFF -DLLAMA_OPENSSL=OFF \
 -DCMAKE_EXE_LINKER_FLAGS="-Wl,-z,max-page-size=16384"
cmake --build "$ROOT/vulkan-build/build" --target llama-server -j "${GPU_BUILD_JOBS:-4}"
BIN="$ROOT/vulkan-build/build/bin/llama-server"
test -s "$BIN"
cp -f "$BIN" "$TARGET/libllama-vulkan-server.so"
"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-debug "$TARGET/libllama-vulkan-server.so" || true
readelf -h "$TARGET/libllama-vulkan-server.so" | grep -q 'AArch64'
readelf -h "$TARGET/libllama-vulkan-server.so" | grep -q 'DYN'
file "$TARGET/libllama-vulkan-server.so"
