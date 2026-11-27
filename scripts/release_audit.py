#!/usr/bin/env python3
"""Release-artifact audit: 16 KiB ELF LOAD segments, native payload and APK ZIP alignment.
This is a static check, not a device or store-approval test."""
import argparse
import hashlib
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

PAGE = 16384
NATIVE_REQUIRED = {
    "libollama.so", "libllama-server.so", "libdeviceprobe.so",
    "libllama-vulkan-server.so", "libggml-rpc-worker.so", "libllama-distributed-server.so",
    "libonhand_whisper.so", "libonhand_tts.so", "libsherpa-onnx-c-api.so", "libonnxruntime.so",
}

def inspect_elf(stream, size):
    header = stream.read(64)
    if len(header) != 64 or header[:6] != b"\x7fELF\x02\x01":
        raise ValueError("not a little-endian ELF64 file")
    values = struct.unpack("<16sHHIQQQIHHHHHH", header)
    _, kind, machine, version, _, phoff, _, _, ehsize, entsize, count, *_ = values
    if machine != 183 or kind not in (2, 3) or version != 1 or ehsize != 64:
        raise ValueError("invalid AArch64 executable/shared object")
    if entsize != 56 or not 1 <= count <= 1024 or phoff < 64 or phoff + count * entsize > size:
        raise ValueError("invalid program header table")
    stream.seek(phoff)
    alignments = []
    errors = []
    for _ in range(count):
        row = stream.read(56)
        if len(row) != 56:
            raise ValueError("truncated program header")
        typ, _, off, vaddr, _, filesz, memsz, align = struct.unpack("<IIQQQQQQ", row)
        if typ != 1:
            continue
        alignments.append(align)
        if filesz > memsz or off + filesz > size:
            errors.append("invalid PT_LOAD bounds")
        if align < PAGE or align & (align - 1):
            errors.append("PT_LOAD alignment below 16 KiB or not power of two")
        if off % PAGE != vaddr % PAGE:
            errors.append("PT_LOAD offset/address mismatch for 16 KiB")
    if not alignments:
        errors.append("no PT_LOAD segments")
    return alignments, errors

def inspect_zip(path, require_coop=True):
    kind = Path(path).suffix.lower()
    if kind not in (".apk", ".aab"):
        raise ValueError("expected .apk or .aab")
    prefix = r"lib/" if kind == ".apk" else r"[^/]+/lib/"
    pattern = re.compile(prefix + r"([^/]+)/([^/]+\.so)$")
    rows, errors, names = [], [], set()
    with open(path, "rb") as raw, zipfile.ZipFile(path) as package:
        seen = set()
        for item in package.infolist():
            match = pattern.fullmatch(item.filename)
            if not match:
                continue
            abi, name = match.groups()
            entry = {"path": item.filename, "size": item.file_size, "compression": item.compress_type}
            problems = []
            if item.filename in seen:
                problems.append("duplicate native entry")
            seen.add(item.filename)
            if abi != "arm64-v8a":
                problems.append("unexpected native ABI")
            else:
                names.add(name)
                try:
                    with package.open(item) as stream:
                        entry["load_alignments"], issues = inspect_elf(stream, item.file_size)
                    problems.extend(issues)
                except (OSError, ValueError, struct.error, zipfile.BadZipFile) as exc:
                    problems.append(str(exc))
            if kind == ".apk" and item.compress_type == zipfile.ZIP_STORED:
                raw.seek(item.header_offset)
                header = raw.read(30)
                if len(header) != 30 or header[:4] != b"PK\x03\x04":
                    problems.append("invalid ZIP local header")
                else:
                    name_len, extra_len = struct.unpack_from("<HH", header, 26)
                    offset = item.header_offset + 30 + name_len + extra_len
                    entry["zip_offset"] = offset
                    if offset % PAGE:
                        problems.append("stored native APK entry is not 16 KiB aligned")
            entry["errors"] = problems
            rows.append(entry)
            errors.extend(item.filename + ": " + issue for issue in problems)
        if not rows:
            errors.append("no native libraries found")
        if require_coop:
            errors.extend("missing " + x for x in sorted(NATIVE_REQUIRED - names))
    with open(path, "rb") as f:
        digest = hashlib.file_digest(f, "sha256").hexdigest()
    return {"artifact": Path(path).name, "sha256": digest, "kind": kind[1:],
            "status": "FAIL" if errors else "PASS", "native": rows, "errors": errors,
            "scope": "static ELF/PT_LOAD and stored APK ZIP alignment only; no runtime or Play approval"}

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("artifact")
    ap.add_argument("--report", required=True)
    ap.add_argument("--partial-payload", action="store_true")
    args = ap.parse_args()
    try:
        report = inspect_zip(args.artifact, not args.partial_payload)
    except Exception as exc:
        report = {"artifact": Path(args.artifact).name, "status": "FAIL", "errors": [str(exc)]}
    Path(args.report).write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(report["status"], args.report)
    for issue in report["errors"]:
        print(issue)
    return int(report["status"] != "PASS")

if __name__ == "__main__":
    sys.exit(main())
