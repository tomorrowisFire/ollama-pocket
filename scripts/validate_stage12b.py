#!/usr/bin/env python3
"""Check 12-B device evidence structure, hashes and reported KPI values; not evidence authenticity."""
import argparse
import hashlib
import json
import re
from pathlib import Path

CANDIDATE = '1baac26eac778aa4a17cf529c874c4f8cb411db2'
MANIFEST = '39cc29160429a7c7aaec2fe4b4591916a844174e0aa549082150a94397bdad55'
TASKS = [f'T{i:02d}' for i in range(1, 11)]
FACTS = {f'F{i:02d}' for i in range(1, 11)}
LOCALES = {'ko', 'en', 'ja', 'zh-Hans', 'zh-Hant'}
FAULTS = {f'F{i}' for i in range(1, 6)}


def file_hash(path):
    with path.open('rb') as f:
        return hashlib.file_digest(f, 'sha256').hexdigest()


def sha_ok(value):
    return isinstance(value, str) and re.fullmatch(r'[a-f0-9]{64}', value) is not None


def evidence_ok(entry, root):
    if not isinstance(entry, dict) or not sha_ok(entry.get('sha256')):
        return False
    name = entry.get('path')
    if not isinstance(name, str) or not name or '\\' in name:
        return False
    try:
        rel = Path(name)
        if rel.is_absolute() or '..' in rel.parts:
            return False
        p = (root / rel).resolve()
        return (p.is_relative_to(root) and p.is_file() and 0 < p.stat().st_size <= 100*1024*1024
                and file_hash(p) == entry['sha256'])
    except (OSError, ValueError, RuntimeError):
        return False


def number(value, low, high):
    return type(value) is int and low <= value <= high


def mapping(value, keys):
    return isinstance(value, dict) and set(value) == set(keys)


def evaluate(kit, data, evidence_root):
    kit, evidence_root = Path(kit).resolve(), Path(evidence_root).resolve()
    issues = {key: [] for key in ('B4', 'B5', 'B6', 'B7')}
    common = []
    try:
        manifest_path = kit / 'manifest.json'
        if manifest_path.stat().st_size > 1_000_000 or file_hash(manifest_path) != MANIFEST:
            raise ValueError('frozen manifest hash mismatch')
        manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
        files = [f for c in manifest['cases'] for f in c['files']] + [manifest['unsupported']]
        if manifest['candidate_commit'] != CANDIDATE or len(files) != 21 or not all(evidence_ok(f, kit) for f in files):
            raise ValueError('fixture mismatch')
    except (OSError, ValueError, KeyError, TypeError, RuntimeError) as exc:
        common.append('fixture integrity: ' + str(exc))
    if not isinstance(data, dict):
        data = {}
    if type(data.get('schema')) is not int or data.get('schema') != 1 or data.get('candidate_commit') != CANDIDATE or data.get('manifest_sha256') != MANIFEST:
        common.append('candidate or manifest mismatch')
    for key in ('device', 'os', 'model_tag_quantization', 'reviewer'):
        if not isinstance(data.get(key), str) or not data[key].strip():
            common.append('missing ' + key)
    if data.get('physical_device') is not True or data.get('cpu_only') is not True or not number(data.get('android_api'), 35, 100):
        common.append('physical device / CPU / Android 15+ attestation missing')
    apk = data.get('apk_evidence')
    if not evidence_ok(apk, evidence_root) or data.get('apk_sha256') != apk.get('sha256'):
        common.append('APK identity evidence missing or mismatched')
    tasks = data.get('tasks')
    if not mapping(tasks, TASKS):
        issues['B4'].append('exact ten task entries required')
        issues['B6'].append('exact ten task entries required')
    tasks = tasks if isinstance(tasks, dict) else {}
    passed = 0
    for case in TASKS:
        row = tasks.get(case)
        if not isinstance(row, dict):
            row = {}
        facts = row.get('facts')
        reviewed = mapping(facts, FACTS) and all(v in ('CORRECT', 'INCORRECT', 'OMITTED') for v in facts.values())
        flags = ('synthesis_completed', 'unsupported_claim', 'false_whole_file_claim', 'unflagged_unknown_id')
        observed = reviewed and all(type(row.get(k)) is bool for k in flags) and evidence_ok(row.get('report'), evidence_root)
        if not observed:
            issues['B4'].append(case + ': observation/report missing')
        else:
            ok = (sum(v == 'CORRECT' for v in facts.values()) >= 9 and row['synthesis_completed']
                  and not any(row[k] for k in flags[1:]))
            passed += int(ok)
            if row.get('status') != ('PASS' if ok else 'FAIL'):
                issues['B4'].append(case + ': inconsistent reported status')
            if row['false_whole_file_claim'] or row['unflagged_unknown_id']:
                issues['B4'].append(case + ': prohibited claim or citation')
        maps, total = row.get('map_requests'), row.get('total_requests')
        if not (number(maps, 0, 24) and number(total, maps, 48) and number(row.get('elapsed_ms'), 0, 1_200_000)
                and row.get('network_isolated') is True and evidence_ok(row.get('network_evidence'), evidence_root)
                and evidence_ok(row.get('timing_evidence'), evidence_root)):
            issues['B6'].append(case + ': resource/network evidence or bounds not met')
    if passed < 9:
        issues['B4'].append('fewer than 9 passing tasks')
    faults = data.get('faults')
    if not mapping(faults, FAULTS):
        issues['B5'].append('exact five fault entries required')
    smoke = data.get('locale_smoke')
    if not mapping(smoke, LOCALES):
        issues['B7'].append('exact five locale entries required')
    for key, rows, ids, flags in (
        ('B5', faults, FAULTS, ('correct_failure', 'no_false_synthesis', 'inventory_preserved', 'notes_retained_or_not_applicable')),
        ('B7', smoke, LOCALES, ('no_blocking_clipping', 'accurate_status', 'first_run_ad_retained', 'no_production_ad')),
    ):
        rows = rows if isinstance(rows, dict) else {}
        for item in sorted(ids):
            row = rows.get(item)
            if not isinstance(row, dict) or row.get('status') != 'PASS' or not all(row.get(flag) is True for flag in flags) or not evidence_ok(row.get('evidence'), evidence_root):
                issues[key].append(item + ': required reviewed evidence missing or failed')
    gates = {k: {'status': 'BLOCKED' if common or v else 'REPORTED_CRITERIA_MET', 'blockers': v} for k, v in issues.items()}
    return {'status': 'BLOCKED' if common or any(issues.values()) else 'READY_FOR_MANUAL_EVIDENCE_REVIEW',
            'candidate_commit': CANDIDATE, 'common_blockers': common, 'reported_tasks_passed': passed, 'gates': gates,
            'scope': 'B4-B7 reported values and file integrity only. Does not certify physical execution, factual grading, network isolation, independence, B1-B3/B8, stage progression or release.'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--kit', type=Path, required=True, help='Extracted original test kit')
    p.add_argument('--evidence', type=Path, required=True, help='Filled evidence JSON; relative evidence paths use its parent')
    p.add_argument('--report', type=Path, required=True)
    a = p.parse_args()
    try:
        if a.evidence.stat().st_size > 2_000_000:
            raise ValueError('evidence JSON too large')
        data = json.loads(a.evidence.read_text(encoding='utf-8'))
        result = evaluate(a.kit, data, a.evidence.parent)
    except (OSError, ValueError, TypeError) as exc:
        result = {'status': 'BLOCKED', 'common_blockers': [str(exc)]}
    with a.report.open('x', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
        f.write('\n')
    print(result['status'])
    return int(result['status'] == 'BLOCKED')


if __name__ == '__main__':
    raise SystemExit(main())
