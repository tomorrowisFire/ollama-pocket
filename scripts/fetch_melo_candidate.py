#!/usr/bin/env python3
"""Host-only data fixture acquisition. Never check out or run upstream code."""
import hashlib
import os
import pathlib
import re
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request

REPO = "https://huggingface.co/csukuangfj/vits-melo-tts-zh_en"
REV = "a0d5c6a264c0ef92d70d8661d8cc502d79627cd6"
MODEL_BYTES = 170429550
MODEL_SHA = "bf30582eb1b012250a35b1a4a80e7dfbcf8485e7bb9de0d95efbbeef0e4ad86d"
# No quantized weights from a different token revision, dictionary tree, or code.
FILES = {
    "LICENSE": 65536,
    "tokens.txt": 131072,
    "lexicon.txt": 16 * 1024 * 1024,
    "date.fst": 2 * 1024 * 1024,
    "number.fst": 2 * 1024 * 1024,
    "phone.fst": 2 * 1024 * 1024,
    "model.onnx": MODEL_BYTES,
}
POINTER = re.compile(rb"version https://git-lfs.github.com/spec/v1\r?\noid sha256:([0-9a-f]{64})\r?\nsize ([0-9]{1,9})\r?\n?")


def trusted(url):
    p = urllib.parse.urlsplit(url)
    host = p.hostname or ""
    if (p.scheme != "https" or p.username is not None or p.password is not None
            or p.port not in (None, 443) or p.fragment
            or not (host == "huggingface.co" or host.endswith(".huggingface.co")
                    or host == "hf.co" or host.endswith(".hf.co"))):
        raise ValueError("Untrusted model download redirect")


class Redirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        trusted(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def git(root, *args, timeout=60):
    env = dict(os.environ, GIT_TERMINAL_PROMPT="0", GIT_LFS_SKIP_SMUDGE="1",
               GIT_CONFIG_NOSYSTEM="1", GIT_CONFIG_GLOBAL=os.devnull)
    return subprocess.run(
        ["git", "-c", "credential.helper=", "-c", "protocol.file.allow=never", *args],
        cwd=root, env=env, check=True, stdout=subprocess.PIPE, timeout=timeout
    ).stdout


def read_blob(root, name, limit):
    row = git(root, "ls-tree", "-z", REV, "--", name)
    if not row.endswith(b"\0") or row.count(b"\0") != 1:
        raise ValueError("Missing/ambiguous pinned asset: " + name)
    meta, actual_name = row[:-1].split(b"\t", 1)
    mode, kind, oid = meta.decode("ascii").split()
    if mode not in ("100644", "100755") or kind != "blob" or actual_name != name.encode("ascii"):
        raise ValueError("Unexpected pinned asset type: " + name)
    if not re.fullmatch("[0-9a-f]{40}", oid):
        raise ValueError("Unexpected Git object ID")
    size = int(git(root, "cat-file", "-s", oid))
    if not 0 < size <= limit:
        raise ValueError("Pinned Git blob bounds: " + name)
    data = git(root, "cat-file", "blob", oid)
    header = ("blob %d\0" % len(data)).encode("ascii")
    if len(data) != size or hashlib.sha1(header + data).hexdigest() != oid:
        raise ValueError("Pinned Git blob mismatch: " + name)
    return data


def download(name, target, size, digest):
    url = REPO + "/resolve/" + REV + "/" + name
    trusted(url)
    opener = urllib.request.build_opener(Redirect())
    req = urllib.request.Request(url, headers={
        "User-Agent": "OnHand-Model-Fixture/1", "Accept-Encoding": "identity",
    })
    total = 0
    sha = hashlib.sha256()
    with opener.open(req, timeout=60) as response, target.open("xb") as out:
        trusted(response.geturl())
        if response.status != 200 or response.headers.get("Content-Encoding", "identity") != "identity":
            raise ValueError("Unexpected model response")
        length = response.headers.get("Content-Length")
        if length is not None and int(length) != size:
            raise ValueError("Model response size changed")
        while True:
            chunk = response.read(min(1024 * 1024, size - total + 1))
            if not chunk:
                break
            total += len(chunk)
            if total > size:
                raise ValueError("Oversized pinned asset")
            sha.update(chunk)
            out.write(chunk)
    if total != size or sha.hexdigest() != digest:
        raise ValueError("Pinned LFS data mismatch: " + name)
    return total, digest


def prepare(destination):
    if not destination.is_dir() or destination.is_symlink() or any(destination.iterdir()):
        raise ValueError("Fixture destination must be a fresh empty directory")
    with tempfile.TemporaryDirectory(prefix="onhand-melo-git-") as root:
        git(root, "init", "-q")
        git(root, "fetch", "--no-tags", "--no-recurse-submodules", "--depth=1",
            REPO, REV, timeout=180)
        if git(root, "rev-parse", "FETCH_HEAD").decode("ascii").strip() != REV:
            raise ValueError("Pinned MeloTTS revision mismatch")
        git(root, "fsck", "--full", "--no-reflogs", timeout=120)
        for name, limit in FILES.items():
            data = read_blob(root, name, 2048 if name == "model.onnx" else limit)
            pointer = POINTER.fullmatch(data)
            target = destination / name
            if name == "model.onnx":
                if not pointer or (int(pointer.group(2)), pointer.group(1).decode()) != (MODEL_BYTES, MODEL_SHA):
                    raise ValueError("MeloTTS model pointer does not match reviewed weights")
            if pointer:
                size = int(pointer.group(2))
                digest = pointer.group(1).decode("ascii")
                if not 0 < size <= limit:
                    raise ValueError("Pinned LFS size limit: " + name)
                size, digest = download(name, target, size, digest)
            else:
                if name == "LICENSE" and (b"MyShell.ai" not in data or b"Permission is hereby granted" not in data):
                    raise ValueError("Unexpected converted model license")
                with target.open("xb") as out:
                    out.write(data)
                size, digest = len(data), hashlib.sha256(data).hexdigest()
            if name == "LICENSE":
                license_data = target.read_bytes()
                if b"MyShell.ai" not in license_data or b"Permission is hereby granted" not in license_data:
                    raise ValueError("Unexpected converted model license")
            print("MELO_ASSET", name, size, digest, flush=True)
    print("PASS: pinned MeloTTS data fixture and converted license", REV, flush=True)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("fresh fixture directory required")
    prepare(pathlib.Path(sys.argv[1]))
