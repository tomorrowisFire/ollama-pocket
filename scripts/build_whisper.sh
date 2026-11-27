#!/usr/bin/env bash
# Pinned CPU Whisper, isolated from the app's llama/GGML binaries.
set -euo pipefail
cd "$(dirname "$0")/.."
WHISPER_ROOT="$PWD"
WHISPER_PIN=96ec4d9823e9d0211944172da35f9f29134af990
WHISPER_WORK="${ONHAND_WHISPER_WORK:-$WHISPER_ROOT/native-voice-src}"
WHISPER_SRC="${ONHAND_WHISPER_SOURCE_DIR:-$WHISPER_WORK/whisper.cpp}"
WHISPER_MODE="${1:-android}"
mkdir -p "$WHISPER_WORK"
if [[ ! -d "$WHISPER_SRC/.git" ]]; then
  git init "$WHISPER_SRC"
  git -C "$WHISPER_SRC" remote add origin https://github.com/ggml-org/whisper.cpp.git
  git -C "$WHISPER_SRC" fetch --depth 1 origin "$WHISPER_PIN"
  git -C "$WHISPER_SRC" checkout --detach FETCH_HEAD
fi
test "$(git -C "$WHISPER_SRC" rev-parse HEAD)" = "$WHISPER_PIN"
test -z "$(git -C "$WHISPER_SRC" status --porcelain --untracked-files=no)"
WHISPER_BUILD="$WHISPER_WORK/build-$WHISPER_MODE"
WHISPER_ARGS=(-DCMAKE_BUILD_TYPE=Release -DCMAKE_POLICY_VERSION_MINIMUM=3.5
  -DONHAND_WHISPER_SOURCE="$WHISPER_SRC")
case "$WHISPER_MODE" in
  host)
    WHISPER_ARGS+=(-DONHAND_WHISPER_HOST_TEST=ON)
    WHISPER_TARGET=(onhand_whisper_smoke)
    if [[ "${ONHAND_WHISPER_TEST_JNI:-0}" == 1 ]]; then
      WHISPER_ARGS+=(-DONHAND_WHISPER_HOST_JNI=ON)
      WHISPER_TARGET+=(onhand_whisper)
    fi
    ;;
  host-jni)
    WHISPER_ARGS+=(-DONHAND_WHISPER_HOST_JNI=ON)
    WHISPER_TARGET=(onhand_whisper)
    ;;
  android)
    : "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to Android NDK directory}"
    WHISPER_ARGS+=(-DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake"
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DANDROID_STL=c++_static)
    WHISPER_TARGET=(onhand_whisper)
    ;;
  *) printf 'Unknown Whisper build mode\n' >&2; exit 2 ;;
esac
cmake -S "$WHISPER_ROOT/app/src/main/cpp/whisper" -B "$WHISPER_BUILD" -G Ninja "${WHISPER_ARGS[@]}"
cmake --build "$WHISPER_BUILD" --target "${WHISPER_TARGET[@]}" --parallel "${ONHAND_WHISPER_BUILD_JOBS:-4}"
if [[ "$WHISPER_MODE" == android ]]; then
  WHISPER_DEST="$WHISPER_ROOT/app/src/main/jniLibs/arm64-v8a/libonhand_whisper.so"
  mkdir -p "$(dirname "$WHISPER_DEST")"
  cp "$WHISPER_BUILD/libonhand_whisper.so" "$WHISPER_DEST"
  WHISPER_LLVM="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
  "$WHISPER_LLVM/llvm-strip" --strip-debug "$WHISPER_DEST"
  "$WHISPER_LLVM/llvm-readelf" -h "$WHISPER_DEST" | grep -q AArch64
  "$WHISPER_LLVM/llvm-nm" -D --defined-only "$WHISPER_DEST" | grep -q ' Java_com_ollamapocket_app_NativeWhisper_transcribe$'
  if "$WHISPER_LLVM/llvm-nm" -D --defined-only "$WHISPER_DEST" | grep -E ' [A-Za-z] (ggml_|whisper_)'; then
    printf 'Whisper/GGML symbol isolation failed\n' >&2; exit 1
  fi
  "$WHISPER_LLVM/llvm-readelf" -lW "$WHISPER_DEST" | awk '$1=="LOAD" { if ($NF != "0x4000" && $NF != "0x10000") bad=1; count++ } END { exit(bad || count==0) }'
  sha256sum "$WHISPER_DEST"
fi
