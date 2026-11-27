#!/usr/bin/env bash
# Real-model host fixture only; this is not a microphone/device quality test.
set -euo pipefail
cd "$(dirname "$0")/.."
WHISPER_TEST_ROOT="$PWD"
WHISPER_TEST_WORK="${ONHAND_WHISPER_WORK:-$WHISPER_TEST_ROOT/native-voice-src}"
WHISPER_TEST_SOURCE="${ONHAND_WHISPER_SOURCE_DIR:-$WHISPER_TEST_WORK/whisper.cpp}"
WHISPER_TEST_MODEL="${ONHAND_WHISPER_MODEL:-$WHISPER_TEST_WORK/ggml-tiny.bin}"
export ONHAND_WHISPER_TEST_JNI=1
bash scripts/build_whisper.sh host
if [[ ! -f "$WHISPER_TEST_MODEL" ]]; then
  curl --fail --location --proto '=https' --proto-redir '=https' --retry 2 --connect-timeout 20 --max-time 600 --max-filesize 77691713 \
    'https://huggingface.co/ggerganov/whisper.cpp/resolve/fb5ed9369c26eaf08392504d8446da431e0fffb9/ggml-tiny.bin' \
    -o "$WHISPER_TEST_MODEL.part"
  printf '%s  %s\n' 'be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21' "$WHISPER_TEST_MODEL.part" | sha256sum -c -
  test "$(wc -c < "$WHISPER_TEST_MODEL.part")" -eq 77691713
  mv "$WHISPER_TEST_MODEL.part" "$WHISPER_TEST_MODEL"
fi
printf '%s  %s\n' 'be07e048e1e599ad46341c8d2a135645097a538221678b7acdd1b1919c6e1b21' "$WHISPER_TEST_MODEL" | sha256sum -c -
test "$(wc -c < "$WHISPER_TEST_MODEL")" -eq 77691713
"$WHISPER_TEST_WORK/build-host/onhand_whisper_smoke" "$WHISPER_TEST_MODEL" "$WHISPER_TEST_SOURCE/samples/jfk.wav"
mkdir -p "$WHISPER_TEST_WORK/jni-test-classes"
javac --release 17 -d "$WHISPER_TEST_WORK/jni-test-classes" \
  app/src/main/java/com/ollamapocket/app/NativeWhisper.java tests/NativeWhisperHostTest.java
java -Djava.library.path="$WHISPER_TEST_WORK/build-host" -cp "$WHISPER_TEST_WORK/jni-test-classes" \
  com.ollamapocket.app.NativeWhisperHostTest "$WHISPER_TEST_MODEL" "$WHISPER_TEST_SOURCE/samples/jfk.wav"
