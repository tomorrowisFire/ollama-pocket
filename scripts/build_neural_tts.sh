#!/usr/bin/env bash
# Build only our JNI bridge; vendor two digest-verified ARM64 runtime libraries.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE="${NEURAL_TTS_CACHE:-$ROOT/build/neural-tts-cache}"
OUT="$ROOT/app/src/main/jniLibs/arm64-v8a"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-${ANDROID_NDK:-}}}"
if [[ -z "$NDK" ]]; then echo "Set ANDROID_NDK_HOME" >&2; exit 1; fi
HOST="$(uname -s | tr '[:upper:]' '[:lower:]')-x86_64"
CXX="$NDK/toolchains/llvm/prebuilt/$HOST/bin/aarch64-linux-android26-clang++"
READELF="$NDK/toolchains/llvm/prebuilt/$HOST/bin/llvm-readelf"
NM="$NDK/toolchains/llvm/prebuilt/$HOST/bin/llvm-nm"
mkdir -p "$CACHE" "$OUT"
python3 - "$CACHE" "$OUT" <<'PY'
import hashlib,pathlib,sys,urllib.request,zipfile
cache,out=map(pathlib.Path,sys.argv[1:])
def artifact(name,url,size,digest):
    p=cache/name
    def good():
        if not p.is_file() or p.stat().st_size!=size:return False
        with p.open('rb') as f:return hashlib.file_digest(f,'sha256').hexdigest()==digest
    if not good():
        temp=p.with_suffix(p.suffix+'.part')
        try:
            with urllib.request.urlopen(url,timeout=60) as r,temp.open('wb') as f:
                total=0
                while True:
                    chunk=r.read(min(1024*1024,size-total+1))
                    if not chunk:break
                    total+=len(chunk)
                    if total>size:raise ValueError('Oversized runtime input')
                    f.write(chunk)
            temp.replace(p)
        finally:temp.unlink(missing_ok=True)
    if not good():raise ValueError('Runtime input digest mismatch: '+name)
    return p
p=artifact('sherpa-onnx-2.0.3.aar',
 'https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.3/sherpa-onnx-2.0.3.aar',
 52411426,'4c766e2e74c9d33d595338c21fe3b305c73ba45d47e96a235675c33142a2ec50')
with zipfile.ZipFile(p) as z:
    for name,digest in {
      'libonnxruntime.so':'33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1',
      'libsherpa-onnx-c-api.so':'109347a4e3a0934cdbecfe1a09d0b524e74fb42039c1a57c74af43f4b26fafc1'
    }.items():
        data=z.read('jni/arm64-v8a/'+name)
        if hashlib.sha256(data).hexdigest()!=digest:raise ValueError('Runtime library digest mismatch')
        (out/name).write_bytes(data)
# This public ABI header is verified independently from the binaries.
header=cache/'c-api.h'
data=header.read_bytes() if header.is_file() else b''
if hashlib.sha256(data).hexdigest()!='1f738de63a206328253ff471edda96b011fa1bd6f2a028f82967a7761abc2edd':
    data=urllib.request.urlopen('https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/38016c6e0622e90bf417c35e8b47e0eb805af417/sherpa-onnx/c-api/c-api.h',timeout=60).read(262145)
if len(data)>262144 or hashlib.sha256(data).hexdigest()!='1f738de63a206328253ff471edda96b011fa1bd6f2a028f82967a7761abc2edd':raise ValueError('C API header digest mismatch')
header.write_bytes(data)
PY
"$CXX" -std=c++17 -O2 -fPIC -fvisibility=hidden -fvisibility-inlines-hidden -shared -static-libstdc++ \
  -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384 -Wl,--exclude-libs,ALL \
  -Wl,--no-undefined -Wl,--version-script="$ROOT/app/src/main/cpp/neural_tts/exports.map" \
  -I"$CACHE" "$ROOT/app/src/main/cpp/neural_tts/neural_tts_jni.cpp" \
  -L"$OUT" -lsherpa-onnx-c-api -o "$OUT/libonhand_tts.so"
"$NM" -D --defined-only "$OUT/libonhand_tts.so" | grep 'Java_com_ollamapocket_app_NativeNeuralTts_nativeOpen$'
"$NM" -D --defined-only "$OUT/libonhand_tts.so" | grep 'Java_com_ollamapocket_app_NativeNeuralTts_nativeGenerate$'
"$NM" -D --defined-only "$OUT/libonhand_tts.so" | grep 'Java_com_ollamapocket_app_NativeNeuralTts_nativeClose$'
python3 - "$READELF" "$OUT" <<'PY'
import pathlib,subprocess,sys
for name in ['libonhand_tts.so','libsherpa-onnx-c-api.so','libonnxruntime.so']:
    p=pathlib.Path(sys.argv[2])/name
    lines=subprocess.check_output([sys.argv[1],'-lW',str(p)],text=True).splitlines()
    loads=[line.split() for line in lines if line.strip().startswith('LOAD')]
    if not loads or any(int(row[-1],16)<16384 for row in loads):raise SystemExit('16 KiB failure: '+name)
    print('PASS: TTS native 16 KiB '+name)
PY
