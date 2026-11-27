#!/usr/bin/env python3
"""Actual pinned x86_64 CPU VLM qualification, entirely separate from Android packaging."""
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tarfile
import tempfile
import time
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/ollamapocket/app"
REV = "b9e4379657e1450d04d02eec8e345667265b0a00"
BASE = "https://huggingface.co/ggml-org/SmolVLM-256M-Instruct-GGUF/resolve/" + REV + "/"
BACKEND = "https://github.com/ggml-org/llama.cpp/releases/download/b11913/llama-b11913-bin-ubuntu-x64.tar.gz"
ARCHIVE = (BACKEND, 22474748, "c670ad826f59478db859c9d4a0d931ab9eb653f22b7e0adebd70816c4092aab5")
MODEL = ("SmolVLM-256M-Instruct-Q8_0.gguf", 175054528, "2a31195d3769c0b0fd0a4906201666108834848db768af11de1d2cef7cd35e65")
PROJECTOR = ("mmproj-SmolVLM-256M-Instruct-Q8_0.gguf", 103769856, "7e943f7c53f0382a6fc41b6ee0c2def63ba4fded9ab8ed039cc9e2ab905e0edd")
MAX_RESPONSE = 256 * 1024


def trusted(url):
    parsed = urlsplit(url)
    host = parsed.hostname or ""
    if (parsed.scheme != "https" or parsed.username is not None or parsed.password is not None
            or parsed.port not in (None, 443) or parsed.fragment
            or not (host in ("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com",
                            "huggingface.co", "hf.co")
                    or host.endswith(".huggingface.co") or host.endswith(".hf.co"))):
        raise ValueError("Untrusted asset URL")


class Redirect(HTTPRedirectHandler):
    max_redirections = 5
    max_repeats = 2

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        trusted(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


def fetch(url, size, sha, path):
    trusted(url)
    opener = build_opener(ProxyHandler({}), Redirect())
    deadline = time.monotonic() + 300
    digest = hashlib.sha256()
    total = 0
    part = path.with_suffix(path.suffix + ".part")
    try:
        with opener.open(Request(url, headers={"Accept-Encoding":"identity", "User-Agent":"OnHand-VLM-CI/1"}), timeout=45) as src:
            trusted(src.geturl())
            if src.status != 200 or src.headers.get("Content-Encoding", "identity").lower() != "identity":
                raise ValueError("Unexpected asset response")
            length = src.headers.get("Content-Length")
            if length is not None and int(length) != size:
                raise ValueError("Asset response size mismatch")
            with part.open("xb") as dst:
                while True:
                    if time.monotonic() > deadline:
                        raise TimeoutError("Asset download deadline")
                    chunk = src.read(min(1024*1024, size-total+1))
                    if not chunk:
                        break
                    total += len(chunk)
                    if total > size:
                        raise ValueError("Asset exceeds declared size")
                    digest.update(chunk)
                    dst.write(chunk)
        if total != size or digest.hexdigest() != sha:
            raise ValueError("Asset digest mismatch")
        part.replace(path)
        print("VLM_ASSET", path.name, total, sha, flush=True)
        return path
    finally:
        part.unlink(missing_ok=True)


def extract(archive, directory):
    # The release archive is digest-pinned; still reject archive traversal and special files.
    directory.mkdir()
    with tarfile.open(archive, "r:gz") as tf:
        members = tf.getmembers()
        if not 1 <= len(members) <= 512:
            raise ValueError("Archive entry count")
        total = 0
        names = set()
        for member in members:
            name = Path(member.name)
            if name.is_absolute() or ".." in name.parts or member.name in names:
                raise ValueError("Invalid archive path")
            names.add(member.name)
            if not (member.isfile() or member.isdir() or member.issym() or member.islnk()):
                raise ValueError("Unexpected archive type")
            if member.size < 0 or member.size > 128*1024*1024:
                raise ValueError("Archive entry size")
            total += member.size
            if total > 256*1024*1024:
                raise ValueError("Archive expansion limit")
        tf.extractall(directory, members=members, filter="data")
    servers = [p for p in directory.rglob("llama-server") if p.is_file()]
    if len(servers) != 1 or not servers[0].resolve().is_relative_to(directory.resolve()):
        raise ValueError("Expected exact llama-server")
    return servers[0]


def request(port, path, data=None, timeout=60):
    # No redirects, proxies, DNS resolution to external endpoints or remote image URLs.
    connection = http.client.HTTPConnection("127.0.0.1", port, timeout=timeout)
    try:
        connection.request("GET" if data is None else "POST", path, body=data,
                           headers={"Content-Type":"application/json"} if data is not None else {})
        response = connection.getresponse()
        raw = response.read(MAX_RESPONSE+1)
        if len(raw)>MAX_RESPONSE:
            raise ValueError("Response size limit")
        if response.status != 200:
            raise RuntimeError("Server returned HTTP "+str(response.status))
        value = json.loads(raw)
        if not isinstance(value, dict):
            raise ValueError("Invalid JSON response")
        return value
    finally:
        connection.close()


def wait_ready(server, port):
    deadline = time.monotonic()+180
    while time.monotonic()<deadline:
        if server.poll() is not None:
            raise RuntimeError("llama-server exited during startup")
        try:
            if request(port, "/health", timeout=3).get("status") == "ok":
                return
        except (OSError, RuntimeError, ValueError, http.client.HTTPException):
            pass
        time.sleep(1)
    raise TimeoutError("llama-server readiness")


def fixtures(directory):
    classes = directory / "classes"
    classes.mkdir()
    subprocess.run(["javac", "--release", "17", "-d", str(classes),
                    str(JAVA/"VisionImagePolicy.java"), str(JAVA/"VisionModelProfile.java"),
                    str(JAVA/"VisionChatRequest.java"), str(ROOT/"tests/VisionChatHostFixture.java")],
                   check=True, timeout=60)
    subprocess.run(["java", "-Djava.awt.headless=true", "-cp", str(classes),
                    "com.ollamapocket.app.VisionChatHostFixture", str(directory)],
                   check=True, timeout=60)
    red = (directory/"red.json").read_bytes()
    blue = (directory/"blue.json").read_bytes()
    one, two = json.loads(red), json.loads(blue)
    u1 = one["messages"][0]["content"][0]["image_url"]["url"]
    u2 = two["messages"][0]["content"][0]["image_url"]["url"]
    if not all(u.startswith("data:image/jpeg;base64,") for u in (u1,u2)) or u1 == u2:
        raise ValueError("Fixture image identity")
    one["messages"][0]["content"][0]["image_url"]["url"] = ""
    two["messages"][0]["content"][0]["image_url"]["url"] = ""
    if one != two or len(red)>3*1024*1024 or len(blue)>3*1024*1024:
        raise ValueError("Fixtures must differ only in image pixels")
    return {"red":red, "blue":blue}


def check_answer(response, expected):
    choices = response.get("choices")
    if not isinstance(choices,list) or len(choices)!=1:
        raise ValueError("Expected one VLM completion")
    choice = choices[0]
    answer = choice.get("message",{}).get("content")
    usage = response.get("usage",{})
    if (not isinstance(answer,str) or not 1<=len(answer)<=512
            or choice.get("finish_reason") not in ("stop","length")
            or type(usage.get("completion_tokens")) is not int or not 1<=usage["completion_tokens"]<=16
            or type(usage.get("prompt_tokens")) is not int or usage["prompt_tokens"]<=0):
        raise ValueError("Invalid bounded VLM completion")
    words = set(re.findall(r"[a-z]+",answer.lower()))
    wrong = "blue" if expected=="red" else "red"
    if expected not in words or wrong in words:
        raise AssertionError(f"VLM color fixture expected {expected}, received {answer!r}")
    print(f"PASS: actual VLM JPEG {expected}: {answer!r}; prompt_tokens={usage['prompt_tokens']}; completion_tokens={usage['completion_tokens']}", flush=True)


def main():
    with tempfile.TemporaryDirectory(prefix="onhand-vlm-host-") as temporary:
        directory=Path(temporary).resolve()
        requests=fixtures(directory)
        archive=fetch(*ARCHIVE, directory/"llama-host.tar.gz")
        server_path=extract(archive,directory/"runtime")
        model=fetch(BASE+MODEL[0],MODEL[1],MODEL[2],directory/MODEL[0])
        projector=fetch(BASE+PROJECTOR[0],PROJECTOR[1],PROJECTOR[2],directory/PROJECTOR[0])
        # Use no model auto-fetch, no cloud credentials, no server tools and one local slot.
        with socket.socket() as sock:
            sock.bind(("127.0.0.1",0))
            port=sock.getsockname()[1]
        environment=os.environ.copy()
        for key in list(environment):
            if key.startswith(("LLAMA_ARG_", "MTMD_", "GGML_", "HF_")) or key.lower() in ("http_proxy","https_proxy","all_proxy","no_proxy"):
                environment.pop(key,None)
        libraries={str(server_path.parent)}
        libraries.update(str(p.parent) for p in (directory/"runtime").rglob("*.so*") if p.is_file())
        environment["LD_LIBRARY_PATH"]=":".join(sorted(libraries))
        log_path=directory/"server.log"
        args=[str(server_path),"-m",str(model),"--mmproj",str(projector),"--host","127.0.0.1",
              "--port",str(port),"-c","2048","-np","1","-t","2","-ngl","0","--no-mmproj-offload",
              "--alias","onhand-smolvlm-256m-q8"]
        with log_path.open("wb") as log:
            server=subprocess.Popen(args,cwd=directory,env=environment,stdout=log,stderr=subprocess.STDOUT)
            try:
                wait_ready(server,port)
                props=request(port,"/props")
                if props.get("modalities",{}).get("vision") is not True or props.get("model_path")!=str(model):
                    raise AssertionError("Server has not proved the exact loaded visual model")
                print("PASS: pinned llama.cpp host reports actual vision modality",flush=True)
                for color,data in requests.items():
                    check_answer(request(port,"/v1/chat/completions",data,timeout=120),color)
                print("PASS: two image-conditioned completions on pinned local CPU VLM; Android NOT_RUN",flush=True)
            except BaseException:
                log.flush()
                with log_path.open("rb") as report:
                    report.seek(max(0,log_path.stat().st_size-12288))
                    print(report.read().decode("utf-8",errors="replace"),flush=True)
                raise
            finally:
                if server.poll() is None:
                    server.terminate()
                    try:
                        server.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        server.kill()
                        server.wait()


if __name__ == "__main__":
    main()
