#!/usr/bin/env python3
"""Decide only whether to run expensive packaging; never grant release acceptance."""
import os
import re
import subprocess
from pathlib import PurePosixPath

DEV_REF = "refs/heads/dev/on-hands-ai-v012"
CHECKPOINT = "docs/on-hands-native-checkpoint.json"
JAVA_PREFIX = "app/src/main/java/com/ollamapocket/app/"
# Native-facing Java changes are conservative triggers, even when no C/C++ changes.
NATIVE_JAVA = re.compile(r"Native|Engine|Gpu|Rpc|RPC|Runner|InferenceSafety|OllamaHttp|"
                         r"LocalInferenceStream|StructuredInferenceTerminal|Whisper|NeuralTtsPlayer|Sherpa|Nnapi|NNAPI")
SHA = re.compile(r"[0-9a-f]{40}")


def safe_path(path):
    if not isinstance(path, str) or not path or "\\" in path:
        return False
    parts = path.split("/")
    if any(p in ("", ".", "..") for p in parts):
        return False
    if path == CHECKPOINT:
        return False
    if path.startswith(JAVA_PREFIX) and len(parts) == len(JAVA_PREFIX.split("/")):
        name = PurePosixPath(path).name
        return name.endswith(".java") and NATIVE_JAVA.search(name) is None
    return (path.startswith("app/src/main/res/") or path.startswith("tests/")
            or path.startswith("docs/") or path.startswith("scripts/test_")
            or path == ".github/workflows/java-compile.yml")


def decide(event, ref, paths):
    if event != "push" or ref != DEV_REF:
        return True, "manual or non-development run"
    if not paths:
        return True, "missing or empty change evidence"
    if any(not safe_path(path) for path in paths):
        return True, "checkpoint, native-sensitive or unclassified change"
    return False, "Java/resource/test/document-only development increment"


def changed_paths(before, head):
    if not SHA.fullmatch(before or "") or not SHA.fullmatch(head or ""):
        raise ValueError("invalid commit identity")
    if before == "0" * 40 or head == "0" * 40 or before == head:
        raise ValueError("missing commit range")
    subprocess.run(["git", "merge-base", "--is-ancestor", before, head],
                   check=True, capture_output=True, timeout=30)
    result = subprocess.run(["git", "diff", "--no-ext-diff", "--no-renames", "--name-only", "-z", before, head, "--"],
                            check=True, capture_output=True, timeout=30)
    return result.stdout.decode("utf-8", errors="strict").rstrip("\0").split("\0") if result.stdout else []


def main():
    event = os.environ.get("GITHUB_EVENT_NAME", "")
    ref = os.environ.get("GITHUB_REF", "")
    try:
        paths = changed_paths(os.environ.get("BEFORE_SHA", ""), os.environ.get("GITHUB_SHA", "")) if event == "push" and ref == DEV_REF else None
        required, reason = decide(event, ref, paths)
    except (ValueError, OSError, subprocess.SubprocessError):
        required, reason = True, "cannot prove exact change scope; require full build"
    print(f"Native packaging {'REQUIRED' if required else 'DEFERRED_TO_STAGE_CHECKPOINT'}: {reason}")
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
        out.write(f"required={'true' if required else 'false'}\n")
    # The output controls execution only. A deferred job is NOT an APK/AAB/16 KiB pass.
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(f"Native packaging: **{'REQUIRED' if required else 'DEFERRED_TO_STAGE_CHECKPOINT'}**. {reason}.\n")


if __name__ == "__main__":
    main()
