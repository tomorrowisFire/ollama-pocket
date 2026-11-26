#!/usr/bin/env python3
"""Read-only CI receipt collector. Never submits, signs, installs or approves release.

Run with GitHub CLI already authenticated for this repository. It creates a new
private evidence folder; only build-level gates may be populated. Human review
must inspect actual artifact reports, binaries and device evidence separately.
"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

from stage10_gate import GATES, REVIEWERS, evaluate

REPO = "tomorrowisFire/ollama-pocket"
BRANCH = "dev/on-hands-ai-v012"
JAVA_PATH = ".github/workflows/java-compile.yml"
NATIVE_PATH = ".github/workflows/build-coop-apk.yml"
JAVA_STEPS = (
    "Guard first-run advertisement and production lock",
    "Test bounded local attachment text extraction",
    "Test voice queue policy and Unicode boundaries",
    "Test credential, PDF cache and RPC socket ownership policies",
    "Test offline SSE protocol fixtures",
    "Test bounded local inference protocol",
    "Test benchmark identity and engine routing policy",
    "Test bounded local workflow policy",
    "Verify OnHand AI brand and locale resource parity",
    "Test release artifact audit",
    "Verify final evidence-gate rules",
    "Compile Java without native Ollama/Vulkan compilation",
)
NATIVE_STEPS = (
    "Compile app with cooperative session and RPC tunnel",
    "Verify that host, worker and prior inference binaries are packaged",
)
BUNDLE_STEP = "Build unsigned QA bundle (sample ad ID, not for Play submission)"
AUDIT_STEP = "Audit all packaged ARM64 native ELF segments and stored APK alignment"

def api(path):
    # Do not print request environment, authentication or remote error bodies.
    result = subprocess.run(["gh", "api", path], capture_output=True, timeout=45, check=True)
    if len(result.stdout) > 8_000_000:
        raise ValueError("CI metadata limit")
    return json.loads(result.stdout)

def valid_run(run, run_id, commit, workflow):
    return (isinstance(run, dict) and run.get("id") == run_id
            and run.get("head_sha") == commit and run.get("head_branch") == BRANCH
            and run.get("path") == workflow
            and isinstance(run.get("repository"), dict)
            and run["repository"].get("full_name") == REPO
            and type(run.get("run_attempt")) is int and run["run_attempt"] >= 1)

def job_pass(jobs, name, required):
    if not isinstance(jobs, list):
        return False
    matching = [j for j in jobs if isinstance(j, dict) and j.get("name") == name]
    if len(matching) != 1:
        return False
    j = matching[0]
    if j.get("status") != "completed" or j.get("conclusion") != "success":
        return False
    steps = j.get("steps")
    if not isinstance(steps, list):
        return False
    for key in required:
        found = [s for s in steps if isinstance(s, dict) and s.get("name") == key]
        if len(found) != 1 or found[0].get("status") != "completed" or found[0].get("conclusion") != "success":
            return False
    return True

def artifact_valid(artifacts, name):
    found = [a for a in artifacts if isinstance(a, dict) and a.get("name") == name]
    if len(found) != 1:
        return False
    a = found[0]
    return (a.get("expired") is False and type(a.get("id")) is int and a["id"] > 0
            and type(a.get("size_in_bytes")) is int and a["size_in_bytes"] > 0
            and re.fullmatch(r"sha256:[0-9a-f]{64}", str(a.get("digest", ""))) is not None)

def complete_rows(payload, field):
    if not isinstance(payload, dict) or not isinstance(payload.get(field), list):
        raise ValueError("Missing CI metadata")
    rows = payload[field]
    if type(payload.get("total_count")) is not int or payload["total_count"] != len(rows) or len(rows) > 100:
        raise ValueError("Incomplete CI metadata")
    return rows

def fetch_receipt(run_id, commit, workflow):
    path = f"repos/{REPO}/actions/runs/{run_id}"
    run = api(path)
    if not valid_run(run, run_id, commit, workflow):
        raise ValueError("CI run does not match requested commit, branch or workflow")
    jobs = complete_rows(api(f"{path}/attempts/{run['run_attempt']}/jobs?per_page=100"), "jobs")
    artifacts = complete_rows(api(f"{path}/artifacts?per_page=100"), "artifacts")
    again = api(path)
    if (not valid_run(again, run_id, commit, workflow)
            or any(again.get(k) != run.get(k) for k in ("run_attempt", "status", "conclusion", "updated_at"))):
        raise ValueError("CI run changed during collection; rerun explicitly")
    return {
        "run": {k: run.get(k) for k in ("id", "head_sha", "head_branch", "path", "status", "conclusion", "run_attempt", "html_url", "updated_at", "repository")},
        "jobs": [{"name":j.get("name"),"id":j.get("id"),"status":j.get("status"),"conclusion":j.get("conclusion"),
                  "steps":[{k:s.get(k) for k in ("name","status","conclusion")} for s in j.get("steps",[])]} for j in jobs],
        "artifacts": [{k:a.get(k) for k in ("id","name","expired","size_in_bytes","digest")} for a in artifacts],
        "scope": "GitHub API metadata only; artifact digest is the archive digest, not a verified APK/AAB file hash",
    }

def ci_status(java, native):
    out = dict.fromkeys(("java_regression","native_apk","aab_build","native_16k_audit"), "NOT_RUN")
    def success(receipt):
        r = receipt["run"]
        return r.get("status") == "completed" and r.get("conclusion") == "success"
    def unavailable(receipt):
        r = receipt["run"]
        return "FAIL" if r.get("status") == "completed" and r.get("conclusion") != "success" else "PENDING"
    if java:
        out["java_regression"] = (
            "PASS" if success(java) and job_pass(java["jobs"], "compile", JAVA_STEPS)
            else "BLOCKED" if success(java) else unavailable(java)
        )
    if native:
        if not success(native):
            for k in ("native_apk","aab_build","native_16k_audit"):
                out[k] = unavailable(native)
            return out
        jobs, artifacts = native["jobs"], native["artifacts"]
        base = job_pass(jobs,"secure-relay-unit-test",("Test AES-GCM loopback tunnel",)) and job_pass(jobs,"cooperative-apk",NATIVE_STEPS)
        out["native_apk"] = "PASS" if base and artifact_valid(artifacts,"AI-in-Pocket-Cooperative-RPC-APK") else "BLOCKED"
        out["aab_build"] = "PASS" if base and job_pass(jobs,"cooperative-apk",(BUNDLE_STEP,)) and artifact_valid(artifacts,"OnHand-QA-UNSIGNED-NOT-FOR-PLAY") else "BLOCKED"
        # A positive workflow receipt alone is not the actual ELF/alignment report.
        out["native_16k_audit"] = "CI_PASS_REVIEW_REQUIRED" if base and job_pass(jobs,"cooperative-apk",(AUDIT_STEP,)) and artifact_valid(artifacts,"OnHand-Release-Compatibility-Audit") else "BLOCKED"
    return out

def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"path":path.name,"sha256":hashlib.sha256(path.read_bytes()).hexdigest()}

def new_manifest(commit, statuses, refs):
    m = {
        "schema":1, "candidate_commit":commit,
        "gates":{k:{"status":"NOT_RUN","evidence":[]} for k in GATES},
        "reviews":{k:{"status":"NOT_RUN","scores":None,"evidence":[]} for k in REVIEWERS},
        "defects":{"status":"NOT_RUN","unresolved":[],"evidence":[]},
        "note":"Automated CI receipts only. Device tests, signing update, actual audit files, ten reviews and manual approval remain separate.",
    }
    for key,status in statuses.items():
        m["gates"][key]={"status":status,"commit":commit,"evidence":[refs["java" if key=="java_regression" else "native"]] if ("java" if key=="java_regression" else "native") in refs else []}
    return m

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--commit", required=True)
    p.add_argument("--java-run", type=int, required=True)
    p.add_argument("--native-run", type=int)
    p.add_argument("--out", type=Path, required=True)
    a = p.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}",a.commit) or a.java_run<1 or (a.native_run is not None and a.native_run<1):
        p.error("Exact 40-character commit and positive run IDs required")
    try:
        java = fetch_receipt(a.java_run,a.commit,JAVA_PATH)
        native = fetch_receipt(a.native_run,a.commit,NATIVE_PATH) if a.native_run is not None else None
        statuses = ci_status(java,native)
        a.out.mkdir(mode=0o700,parents=True,exist_ok=False)
        refs={"java":write_json(a.out/"java-ci-receipt.json",java)}
        if native:
            refs["native"]=write_json(a.out/"native-ci-receipt.json",native)
        manifest=new_manifest(a.commit,statuses,refs)
        write_json(a.out/"evidence.json",manifest)
        result=evaluate(manifest,a.out)
        write_json(a.out/"gate-status.json",result)
        print("CI receipts:",statuses)
        print("Final release gate:",result["status"],"-",len(result["blockers"]),"blockers")
        return 0 # collection success, NOT release approval
    except (OSError, ValueError, TypeError, KeyError, subprocess.SubprocessError) as exc:
        print("CI receipt collection BLOCKED:", type(exc).__name__)
        return 1

if __name__=="__main__":
    raise SystemExit(main())
