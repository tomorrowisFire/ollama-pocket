#!/usr/bin/env bash
# Android ARM64 CPU-only distributed inference engine, exactly the same sources on every node.
# No unsecured RPC listener is exposed; Android app starts ggml-rpc-server on 127.0.0.1.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to Android NDK directory}"
LLAMA_TAG="b11913"
SRC="$ROOT/cooperative-src/llama.cpp"
BUILD="$ROOT/cooperative-src/build-cpu-rpc"
DEST="$ROOT/app/src/main/jniLibs/arm64-v8a"
mkdir -p "$ROOT/cooperative-src" "$DEST"
if [[ ! -d "$SRC/.git" ]]; then
  git clone --depth 1 --branch "$LLAMA_TAG" https://github.com/ggml-org/llama.cpp.git "$SRC"
fi
# Verify the exact source identity used by the host VLM qualification and Android CPU server.
test "$(git -C "$SRC" rev-parse HEAD)" = "7aca0d2ea8d92b56911233b822db01f9fbb1ae50"
# One pinned tag is used for host llama-server and all worker ggml-rpc-server copies.
cmake -S "$SRC" -B "$BUILD" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_RPC=ON -DGGML_VULKAN=OFF -DGGML_NATIVE=OFF \
  -DGGML_OPENMP=OFF -DGGML_BACKEND_DL=OFF -DBUILD_SHARED_LIBS=OFF \
  -DLLAMA_BUILD_SERVER=ON -DLLAMA_BUILD_UI=OFF -DLLAMA_USE_PREBUILT_UI=OFF \
  -DLLAMA_BUILD_TESTS=OFF -DLLAMA_CURL=OFF -DLLAMA_OPENSSL=OFF \
  -DCMAKE_EXE_LINKER_FLAGS='-Wl,-z,max-page-size=16384'
cmake --build "$BUILD" --target llama-server ggml-rpc-server -j "${CPU_RPC_BUILD_JOBS:-4}"
test -s "$BUILD/bin/llama-server"
test -s "$BUILD/bin/ggml-rpc-server"
cp "$BUILD/bin/llama-server" "$DEST/libllama-distributed-server.so"
cp "$BUILD/bin/ggml-rpc-server" "$DEST/libggml-rpc-worker.so"
STRIP="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
for name in libllama-distributed-server.so libggml-rpc-worker.so; do
  "$STRIP" --strip-debug "$DEST/$name" || true
  readelf -h "$DEST/$name" | grep -q AArch64
  readelf -h "$DEST/$name" | grep -q DYN
  # Android requires bionic dynamic loader, not the Linux/glibc loader.
  readelf -l "$DEST/$name" | grep -q '/system/bin/linker64'
  sha256sum "$DEST/$name"
done
