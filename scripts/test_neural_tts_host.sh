#!/usr/bin/env bash
# Real pinned-model JNI smoke test on Linux x86_64; this is not a device test.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${NEURAL_TTS_CACHE:-$ROOT/build/neural-tts-cache}"
BUILD="$ROOT/build/neural-tts-host"
JDK_ROOT="${JAVA_HOME:-}"
if [[ -z "$JDK_ROOT" ]]; then JDK_ROOT="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"; fi
mkdir -p "$CACHE" "$BUILD/classes"
python3 - "$CACHE" <<'PY'
import hashlib,pathlib,shutil,sys,tarfile,urllib.request
cache=pathlib.Path(sys.argv[1])
def fetch(name,url,size,digest):
    path=cache/name
    def valid():
        if not path.is_file() or path.stat().st_size!=size:return False
        with path.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()==digest
    if not valid():
        partial=path.with_suffix(path.suffix+'.part')
        try:
            with urllib.request.urlopen(url,timeout=60) as src,partial.open('wb') as dst:
                total=0
                while True:
                    chunk=src.read(min(1024*1024,size-total+1))
                    if not chunk:break
                    total+=len(chunk)
                    if total>size:raise ValueError('Oversized test input')
                    dst.write(chunk)
            partial.replace(path)
        finally:partial.unlink(missing_ok=True)
    if not valid():raise ValueError('Test input digest mismatch: '+name)
    return path
linux=fetch('sherpa-linux.tar.bz2','https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-v2.0.3-linux-x64-shared-lib.tar.bz2',10072252,'898e710a00ae0ed64e82d8696f65e40969e18c65e664b41fa80c52c4d865bd33')
model=fetch('supertonic3.tar.bz2','https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2',128774318,'82fa96f91c4ef8abaae3a14a3f4153facf88bed821d1f7331cec2700f432c427')
for path in (linux,model):
    with tarfile.open(path) as archive:
        total=0
        for member in archive:
            pure=pathlib.PurePosixPath(member.name)
            if pure.is_absolute() or '..' in pure.parts or not (member.isfile() or member.isdir()):raise ValueError('Unsafe test archive entry')
            total+=member.size
            if member.size>100_000_000 or total>200_000_000:raise ValueError('Test archive bounds')
            target=cache/member.name
            if member.isdir():target.mkdir(parents=True,exist_ok=True);continue
            target.parent.mkdir(parents=True,exist_ok=True)
            with archive.extractfile(member) as src,target.open('wb') as dst:shutil.copyfileobj(src,dst,1024*1024)
header=cache/'c-api.h'
expected='1f738de63a206328253ff471edda96b011fa1bd6f2a028f82967a7761abc2edd'
data=header.read_bytes() if header.is_file() else b''
if hashlib.sha256(data).hexdigest()!=expected:
    data=urllib.request.urlopen('https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/38016c6e0622e90bf417c35e8b47e0eb805af417/sherpa-onnx/c-api/c-api.h',timeout=60).read(262145)
if len(data)>262144 or hashlib.sha256(data).hexdigest()!=expected:raise ValueError('C API header digest mismatch')
header.write_bytes(data)
PY
LIB="$CACHE/sherpa-onnx-v2.0.3-linux-x64-shared-lib/lib"
"${CXX:-c++}" -std=c++17 -O2 -fPIC -shared -fvisibility=hidden -fvisibility-inlines-hidden \
  -Wl,--version-script="$ROOT/app/src/main/cpp/neural_tts/exports.map" \
  -I"$CACHE" -I"$JDK_ROOT/include" -I"$JDK_ROOT/include/linux" \
  "$ROOT/app/src/main/cpp/neural_tts/neural_tts_jni.cpp" -L"$LIB" -lsherpa-onnx-c-api \
  -o "$BUILD/libonhand_tts.so"
"$JDK_ROOT/bin/javac" --release 17 -d "$BUILD/classes" \
  "$ROOT/app/src/main/java/com/ollamapocket/app/NativeNeuralTts.java" \
  "$ROOT/app/src/main/java/com/ollamapocket/app/NeuralTtsPolicy.java" \
  "$ROOT/tests/NeuralTtsPolicyTest.java" "$ROOT/tests/NeuralTtsNativeSmokeTest.java"
"$JDK_ROOT/bin/java" -cp "$BUILD/classes" com.ollamapocket.app.NeuralTtsPolicyTest
LD_LIBRARY_PATH="$LIB${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
  "$JDK_ROOT/bin/java" -Djava.library.path="$BUILD" -cp "$BUILD/classes" \
  com.ollamapocket.app.NeuralTtsNativeSmokeTest "$CACHE/sherpa-onnx-supertonic-3-tts-int8-2026-05-11"

# Separate qualification after the retained Supertonic test: no Android activation.
MELO_DIR="$(mktemp -d "$BUILD/melo.XXXXXX")"
trap 'rm -rf -- "$MELO_DIR"' EXIT
python3 "$ROOT/scripts/fetch_melo_candidate.py" "$MELO_DIR"
LD_LIBRARY_PATH="$LIB${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" \
  "$JDK_ROOT/bin/java" -Djava.library.path="$BUILD" -cp "$BUILD/classes" \
  com.ollamapocket.app.NeuralTtsNativeSmokeTest --melo "$MELO_DIR"
