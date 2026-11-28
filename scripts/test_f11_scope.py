#!/usr/bin/env python3
"""Verify the exact F11-3..7 tracked-tree delta, not runtime data or release approval."""
import json
import re
import subprocess
from pathlib import Path

BASELINE = "fe5be0a8037fa37d5c1cce77752c60946ec9216d"
STAGE = "F11-7-integration-candidate"
CHECKPOINT = "docs/on-hands-native-checkpoint.json"
MODIFIABLE = frozenset([
  ".github/workflows/java-compile.yml",
  ".github/workflows/build-coop-apk.yml",
  "app/src/main/java/com/ollamapocket/app/AutomationUi.java",
  "app/src/main/java/com/ollamapocket/app/BranchWorkflowRun.java",
  "app/src/main/java/com/ollamapocket/app/CloudApi.java",
  "app/src/main/java/com/ollamapocket/app/HuggingFaceCatalog.java",
  "app/src/main/java/com/ollamapocket/app/LocalInferenceStream.java",
  "app/src/main/java/com/ollamapocket/app/ModelSearchCache.java",
  "app/src/main/java/com/ollamapocket/app/ModelSearchWorkers.java",
  "app/src/main/java/com/ollamapocket/app/ModelVariantCatalog.java",
  "app/src/main/java/com/ollamapocket/app/OnHandsVoice.java",
  "app/src/main/java/com/ollamapocket/app/ReviewedLocalPolicy.java",
  "app/src/main/java/com/ollamapocket/app/ToolJsonPolicy.java",
  "app/src/main/java/com/ollamapocket/app/VoiceInteractionPolicy.java",
  "app/src/main/java/com/ollamapocket/app/VoiceQueuePolicy.java",
  "app/src/main/java/com/ollamapocket/app/WhisperCapture.java",
  "app/src/main/java/com/ollamapocket/app/WhisperCapturePolicy.java",
  "app/src/main/java/com/ollamapocket/app/WorkflowBranchPlan.java",
  "app/src/main/res/values/strings.xml",
  "app/src/main/res/values-ko/strings.xml",
  "app/src/main/res/values-b+zh+Hans/strings.xml",
  "app/src/main/res/values-b+zh+Hant/strings.xml",
  "app/src/main/res/values-ja/strings.xml",
  "docs/on-hands-native-checkpoint.json",
  "scripts/test_sidebar_contract.py",
  "scripts/collect_ci_evidence.py",
  "tests/test_collect_ci_evidence.py",
  "tests/CloudStreamTest.java",
  "tests/LocalInferenceStreamTest.java",
  "tests/ModelSearchBatchTest.java",
  "tests/ModelSearchWorkersTest.java",
  "tests/ReviewedExecutionIntegrationTest.java",
  "tests/VoiceInteractionPolicyTest.java",
  "tests/VoiceQueuePolicyTest.java",
  "tests/WhisperCapturePolicyTest.java",
  "tests/WorkflowBranchPlanTest.java"
])
ADDITIONS = frozenset([
  "scripts/test_f11_scope.py",
  "tests/test_f11_scope.py",
  "tests/F11IntegratedPolicyTest.java",
  "docs/on-hands-f11-7-integration.md",
  "docs/on-hands-f11-3-6-integration-r001.md",
  "docs/on-hands-f11-3-6-integration-r002.md",
  "docs/on-hands-f11-3-6-integration-r003.md",
  "docs/on-hands-f11-3-6-integration-r004.md",
  "docs/on-hands-f11-3-6-integration-r005.md",
  "docs/on-hands-f11-3-6-integration-r006.md",
  "docs/on-hands-f11-3-increment-1.md",
  "docs/on-hands-f11-3-increment-2.md",
  "docs/on-hands-f11-3-increment-3.md",
  "docs/on-hands-f11-3-increment-4.md",
  "docs/on-hands-f11-3-increment-5.md",
  "docs/on-hands-f11-3-increment-6.md",
  "docs/on-hands-f11-4-increment-1.md",
  "docs/on-hands-f11-4-increment-2.md",
  "docs/on-hands-f11-4-increment-4.md",
  "docs/on-hands-f11-4-increment-5.md",
  "docs/on-hands-f11-4-increment-6.md",
  "docs/on-hands-f11-5-increment-1.md",
  "docs/on-hands-f11-5-increment-2.md",
  "docs/on-hands-f11-5-increment-3.md",
  "docs/on-hands-f11-5-increment-4.md",
  "docs/on-hands-f11-5-increment-5.md",
  "docs/on-hands-f11-5-increment-6.md",
  "docs/on-hands-f11-6-increment-1.md",
  "docs/on-hands-f11-6-increment-2.md",
  "docs/on-hands-f11-6-increment-3.md",
  "docs/on-hands-f11-6-increment-4.md",
  "docs/on-hands-f11-6-increment-5.md",
  "docs/on-hands-f11-6-increment-6.md",
  "docs/on-hands-f11-4-r003-regression.md"
])
F12_STAGE = "F12-file-intake-integration-candidate"
F12_BASELINE = "0152869a25bfa63e1e6e94c5ebb079ccc0facba8"
F12_MODIFIABLE = frozenset([
  "app/src/main/java/com/ollamapocket/app/ProjectReportHandoff.java",
  "tests/ProjectReportHandoffTest.java",
  ".github/workflows/java-compile.yml",
  "scripts/test_onhand_brand_contract.py",
  "app/src/main/java/com/ollamapocket/app/AdSupport.java",
  "app/src/main/java/com/ollamapocket/app/AutomationUi.java",
  "app/src/main/java/com/ollamapocket/app/ArtifactFileExport.java",
  "tests/ArtifactFileExportTest.java",
  "app/src/main/java/com/ollamapocket/app/DocumentBatchPolicy.java",
  "app/src/main/java/com/ollamapocket/app/DocumentBatchSchedule.java",
  "app/src/main/java/com/ollamapocket/app/DocumentCitationCoverage.java",
  "app/src/main/java/com/ollamapocket/app/DocumentBatchUi.java",
  "app/src/main/java/com/ollamapocket/app/DocumentBatchRunner.java",
  "app/src/main/java/com/ollamapocket/app/DocumentBatchReport.java",
  "tests/DocumentBatchPolicyTest.java",
  "tests/DocumentBatchScheduleTest.java",
  "tests/DocumentCitationCoverageTest.java",
  "tests/DocumentBatchReportTest.java",
  "app/src/main/java/com/ollamapocket/app/KnowledgeLibrary.java",
  "tests/KnowledgeImportPolicyTest.java",
  "app/src/main/java/com/ollamapocket/app/AttachmentText.java",
  "app/src/main/java/com/ollamapocket/app/DocumentWindow.java",
  "app/src/main/java/com/ollamapocket/app/ImageOcr.java",
  "app/src/main/java/com/ollamapocket/app/MainActivity.java",
  "app/src/main/java/com/ollamapocket/app/OcrPolicy.java",
  "app/src/main/java/com/ollamapocket/app/OfficeText.java",
  "app/src/main/java/com/ollamapocket/app/PdfExcerpt.java",
  "app/src/main/java/com/ollamapocket/app/PdfTextClient.java",
  "app/src/main/java/com/ollamapocket/app/VisionImagePolicy.java",
  "app/src/main/res/values/strings.xml",
  "app/src/main/res/values-ko/strings.xml",
  "app/src/main/res/values-b+zh+Hans/strings.xml",
  "app/src/main/res/values-b+zh+Hant/strings.xml",
  "app/src/main/res/values-ja/strings.xml",
  "tests/AttachmentTextTest.java",
  "tests/DocumentWindowTest.java",
  "tests/OcrPolicyTest.java",
  "tests/OfficeTextTest.java",
  "tests/PdfExcerptTest.java",
  "tests/VisionImagePolicyTest.java",
  "docs/on-hands-native-checkpoint.json",
  "scripts/test_f11_scope.py",
  "tests/test_f11_scope.py"
])
F12_ADDITIONS = frozenset([
  "docs/on-hands-f12-input-budget-hardening.md",
  "docs/on-hands-f12-compact-project-report.md",
  "docs/on-hands-f12-r008-integration.md",
  "app/src/main/java/com/ollamapocket/app/DeepDialogueExport.java",
  "tests/DeepDialogueExportTest.java",
  "docs/on-hands-f12-dialogue-export.md",
  "app/src/main/java/com/ollamapocket/app/DeepFollowUpPolicy.java",
  "tests/DeepFollowUpPolicyTest.java",
  "docs/on-hands-f12-follow-up-policy.md",
  "docs/on-hands-f12-follow-up-runner.md",
  "docs/on-hands-f12-follow-up-ui.md",
  "docs/on-hands-f12-reward-recovery.md",
  "docs/on-hands-f12-follow-up-locales.md",
  "docs/on-hands-f12-follow-up-integration.md",
  "app/src/main/java/com/ollamapocket/app/DeepChatPolicy.java",
  "tests/DeepChatPolicyTest.java",
  "docs/on-hands-f12-source-zip.md",
  "docs/on-hands-f12-large-report-export.md",
  "docs/on-hands-f12-deep-chat-policy.md",
  "docs/on-hands-f12-deep-chat-reward.md",
  "docs/on-hands-f12-deep-chat-ui.md",
  "docs/on-hands-f12-deep-chat-locales.md",
  "docs/on-hands-f12-deep-chat-integration.md",
  "docs/on-hands-f12-batch-expansion-policy.md",
  "docs/on-hands-f12-batch-expansion-integration.md",
  "docs/on-hands-f12-file-import-text.md",
  "docs/on-hands-f12-file-import-office.md",
  "docs/on-hands-f12-file-import-media.md",
  "docs/on-hands-f12-file-import-ui.md",
  "docs/on-hands-f12-file-import-integration.md"
])
SHA = re.compile(r"[0-9a-f]{40}")

def git(*args):
    result = subprocess.run(["git", *args], capture_output=True, check=True, timeout=60)
    if len(result.stdout) > 8_000_000:
        raise ValueError("Git tree limit")
    return result.stdout

def read_tree(commit):
    if not SHA.fullmatch(commit or ""):
        raise ValueError("Exact commit required")
    entries = {}
    for record in git("ls-tree", "-r", "-z", commit).split(b"\0"):
        if not record:
            continue
        raw, path = record.split(b"\t", 1)
        mode, kind, digest = raw.decode("ascii").split(" ")
        name = path.decode("utf-8", "strict")
        if name in entries or not SHA.fullmatch(digest):
            raise ValueError("Invalid Git tree")
        entries[name] = (mode, kind, digest)
    if not entries:
        raise ValueError("Empty Git tree")
    return entries

def audit(before, after, modifiable=MODIFIABLE, additions=ADDITIONS):
    failures = []
    for path in sorted(set(before) | set(after)):
        old, new = before.get(path), after.get(path)
        if old == new:
            continue
        if old is None:
            if path not in additions or new[:2] != ("100644", "blob"):
                failures.append("Unexpected addition: " + path)
        elif new is None:
            failures.append("Unexpected deletion: " + path)
        elif path not in modifiable or old[:2] != new[:2] or new[1] != "blob":
            failures.append("Unexpected modification or mode: " + path)
    return failures

def main():
    checkpoint = json.loads(Path(CHECKPOINT).read_text(encoding="utf-8"))
    stage=checkpoint.get("stage")
    if stage not in (STAGE,F12_STAGE):
        print("F11 source scope: NOT_APPLICABLE (different checkpoint)")
        return 0
    baseline=BASELINE if stage==STAGE else F12_BASELINE
    if checkpoint.get("scopeBaseline") != baseline:
        print("Source scope: BLOCKED (baseline identity)")
        return 1
    head = git("rev-parse", "HEAD").decode("ascii").strip()
    git("merge-base", "--is-ancestor", baseline, head)
    before, after = read_tree(baseline), read_tree(head)
    if stage==F12_STAGE:
        git("merge-base", "--is-ancestor", BASELINE, F12_BASELINE)
        failures=audit(read_tree(BASELINE),before)
        failures.extend(audit(before,after,F12_MODIFIABLE,F12_ADDITIONS))
    else:
        failures=audit(before,after)
    for failure in failures:
        print(failure)
    changed = sum(before.get(p) != after.get(p) for p in set(before) | set(after))
    print(stage+" exact tracked-tree scope:", "FAIL" if failures else "PASS",
          "changed", changed, "unchanged", len(set(before) & set(after))-sum(
              before[p] != after[p] for p in set(before) & set(after)))
    return int(bool(failures))

if __name__ == "__main__":
    raise SystemExit(main())
