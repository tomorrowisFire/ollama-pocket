#!/usr/bin/env python3
"""Validate an explicitly supplied local evidence package; never execute tests or certify evidence authenticity."""
import argparse
import hashlib
import json
import re
from pathlib import Path

GATES = ["java_regression","native_apk","aab_build","native_16k_audit","signed_update","api36_4k_device","api36_16k_device","phone_tablet_layout","five_locale_accessibility","local_chat_document_code","voice_stt_tts","cloud_provider_calls","pc_lan_calls","cpu_vulkan_rpc","rpc_zero_model_guest","rpc_disconnect_thermal","mcp_manual_permissions","workflow_10_steps","workflow_resume_cancel","network_privacy_sdk","test_ads_consent","play_policy_review"]
REVIEWERS = ["A","B","C","D","E","F","G","H","I","J"]
DIMENSIONS = {"task":30,"usability":25,"reliability":20,"performance":15,"privacy":10}
MAX_EVIDENCE_BYTES = 100 * 1024 * 1024

def digest(path):
    with path.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()

def evidence_valid(items, root):
    if not isinstance(items, list) or not 1 <= len(items) <= 32:
        return False
    try:
        for item in items:
            if not isinstance(item, dict):
                return False
            rel = item.get("path")
            sha = item.get("sha256")
            if not isinstance(rel, str) or not isinstance(sha, str) or not re.fullmatch(r"[a-f0-9]{64}", sha):
                return False
            candidate = Path(rel)
            if candidate.is_absolute() or ".." in candidate.parts:
                return False
            path = (root / candidate).resolve()
            if not path.is_relative_to(root) or not path.is_file() or not 0 < path.stat().st_size <= MAX_EVIDENCE_BYTES:
                return False
            if digest(path) != sha:
                return False
        return True
    except (OSError, ValueError, RuntimeError):
        return False

def evaluate(manifest, root):
    root = Path(root).resolve()
    blockers = []
    totals = {}
    if not isinstance(manifest, dict) or manifest.get("schema") != 1:
        return {"status":"BLOCKED","blockers":["invalid evidence schema"],"review_scores":{},"mean":None,"minimum":None}
    commit = manifest.get("candidate_commit")
    if not isinstance(commit, str) or not re.fullmatch(r"[a-f0-9]{40}", commit):
        blockers.append("candidate commit missing or invalid")
    for key in GATES:
        group = manifest.get("gates")
        entry = group.get(key) if isinstance(group, dict) else None
        if not isinstance(entry, dict) or entry.get("status") != "PASS" or entry.get("commit") != commit or not evidence_valid(entry.get("evidence"), root):
            blockers.append("gate: " + key)
    for reviewer in REVIEWERS:
        group = manifest.get("reviews")
        entry = group.get(reviewer) if isinstance(group, dict) else None
        if not isinstance(entry, dict) or entry.get("status") != "REVIEWED" or entry.get("commit") != commit or not evidence_valid(entry.get("evidence"), root):
            blockers.append("review: " + reviewer)
            continue
        scores = entry.get("scores")
        if not isinstance(scores, dict) or set(scores) != set(DIMENSIONS) or any(type(scores[k]) is not int or not 0 <= scores[k] <= limit for k, limit in DIMENSIONS.items()):
            blockers.append("invalid rubric: " + reviewer)
            continue
        total = sum(scores.values())
        totals[reviewer] = total
        if total < 90:
            blockers.append("review below 90: " + reviewer)
    mean = sum(totals.values()) / len(REVIEWERS) if len(totals) == len(REVIEWERS) else None
    minimum = min(totals.values()) if len(totals) == len(REVIEWERS) else None
    if mean is None or mean < 90:
        blockers.append("mean below 90 or incomplete")
    defects = manifest.get("defects")
    if not isinstance(defects, dict) or defects.get("status") != "REVIEWED" or defects.get("commit") != commit or not evidence_valid(defects.get("evidence"), root):
        blockers.append("defect review missing")
    else:
        unresolved = defects.get("unresolved")
        if not isinstance(unresolved, list) or any(not isinstance(d, dict) or d.get("severity") not in ("P2","P3") or not isinstance(d.get("id"), str) or not d["id"].strip() for d in unresolved):
            blockers.append("unresolved P0/P1 or invalid defect register")
    return {"status":"BLOCKED" if blockers else "READY_FOR_MANUAL_APPROVAL","candidate_commit":commit,
            "blockers":blockers,"review_scores":totals,"mean":mean,"minimum":minimum,
            "scope":"schema, candidate binding, evidence-file existence and SHA-256 only; human inspection and separate user release approval still required"}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--expect-blocked", action="store_true", help="CI template regression only")
    args = parser.parse_args()
    try:
        if args.manifest.stat().st_size > 2_000_000:
            raise ValueError("manifest limit")
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        result = evaluate(manifest, args.manifest.parent)
    except (OSError, ValueError, TypeError) as exc:
        result = {"status":"BLOCKED","blockers":["invalid manifest: " + str(exc)]}
    args.report.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(result["status"], "blockers:", len(result["blockers"]))
    return int((result["status"] != "BLOCKED") if args.expect_blocked else (result["status"] != "READY_FOR_MANUAL_APPROVAL"))

if __name__ == "__main__":
    raise SystemExit(main())
