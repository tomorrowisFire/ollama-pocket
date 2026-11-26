# OnHand AI — independent-perspective review and release gate

## User-directed review contract
Each improvement stage must be followed by ten independent-perspective *simulated* reviews. They are not ten real users or independently executed agents unless separately demonstrated. No fabricated hands-on evaluations, test passes, or approval claims.

Baseline: v0.12.0 dev commit d5431da0e17ca25c3dcb7a3d9f85f28e6405aae7. Prior mock panel scores A–J: 61,58,72,64,69,60,76,54,62,67; mean 64.3/100. Baseline is a heuristic, not a measured product KPI. Java compile run 45752880517 succeeded, existing RPC/security/unit checks succeeded in run 45752880580, native APK/device tests incomplete at time of record.

## Three-role development conference (Stage 12-B onward)
Before implementation and after verification, record distinct proposals from:
- A — product/UX director: user tasks, discoverability, accessibility and language quality.
- B — engineering lead: architecture, implementation, memory/performance and compatibility.
- C — QA/security lead: adversarial review, reproducible tests, regressions and release risk. Can veto unsupported PASS, release or score changes.

Each role writes its assessment before the joint decision. Record disagreements, final decision, implementation owner, exact baseline/commit, test results and unresolved blockers. Review the highest-risk changes again after CI. Do not count this internal conference as the separate ten-perspective evaluation. Use truly independent agents only if an execution environment actually supports them and provides evidence; otherwise explicitly label the role-based discussion as one assistant's simulated conference. Preserve the existing review and release gates; no vote overrides missing evidence, user-mandated ads, production lock, data integrity or separate release approval.

## Fixed reviewer perspectives
A daily assistant, B documents and file analysis, C coding, D automation/tools, E cloud/API, F voice/accessibility, G local/distributed inference, H UX/visual polish, I localization/global usability, J security/privacy.

Each evaluator scores 0–100 using task completion (30), ease of use (25), reliability (20), performance/resource cost (15), privacy/safety (10), with written evidence, reproducible test case, missing evidence and corrective action. Rate relevant representative tasks rather than counting features. Report ten individual scores, arithmetic mean, minimum, and unresolved blocking findings. Missing real-device or end-to-end evidence must be marked NOT_RUN, never converted to a pass. Do not increase scores solely because a feature was declared or a source anchor exists.

## Execution loop
1. Freeze stage scope and baseline; implement on isolated development branch.
2. Review code, run static checks, unit tests, Android compilation, then stage-specific real-device/functional tests. Preserve existing data, CPU/GPU/RPC, offline usage, package and signing compatibility; keep production ads disabled.
3. Record ten simulated ratings, evidence and deficiencies. Fix highest-priority deficits and rerun both changed and regression checks.
4. Continue only when the stage-specific acceptance tests pass. Do not claim final completion when final score is below 90 or an unresolved P0/P1 blocker remains.

## Final exit gate
Mean >=90/100 and each evaluator >=90/100 (retains previously stated all-reviewer target). In addition: zero unresolved critical/high-severity defects; verified signed install/update without model/history loss; release AAB and applicable Play policy checks; five-language screen/accessibility coverage; offline and multi-device zero-model guest tests; real speech and cloud integrations (authorized test credentials only); independently inspect evidence of performance and privacy. No production ads enabled, no Play submission or merge to main without separate user approval.

If CI, device, keys or permissions are unavailable, mark BLOCKED/NOT_RUN and report what remains. No autonomous background operation is implied. Completion requires evidence, not target score alone.

## User-mandated advertising preservation
First-run onboarding banner must be retained at the original placement and original publisher ad-unit ID; do not remove or disable the placement during UX work. Keep the existing model-download and app-open formats. In development, Google test ad units only; live production requests remain locked until separately authorized, app approval and consent/lifecycle validation completed. The user can dismiss onboarding without interacting with ads or waiting for a fill. Do not allow overlapping ads or accidental clicks. CI regression guard is required for first-run placement and unit preservation.

## Tablet layout gate
Preserve user-selectable Auto / Phone / Tablet layout. Wide windows must show an actual persistent navigation/history pane; compact and split-screen windows must retain the drawer, without forcing undersized content. Settings must persist without touching model/history keys or stopping engines. Test 360/600/720/840/1024dp, portrait/landscape, resize and 126%+ font scaling, keyboard and cutout handling; include first-run ads, local/cloud separation, guest mode and model grid regression. Do not award UI usability PASS from width policy alone.

## Branding change
User-directed display name: **OnHand AI** (no space between On and Hand, singular Hand). Preserve package ID, legacy preference keys, signing identity, RPC wire protocol, and all three original ad placements/IDs. Historic names in prior reports are evidence, not identifiers to migrate.

## Mandatory Stage 08-B: user-started consecutive workflow automation
Separate user-authorized in-app workflow runner from conversational assistant sessions and from developer background work. Automatically progress only after the configured evidence-backed gate passes; retry only within explicit finite budgets. Approval-needed, unknown/unverified, interrupted, unsafe, and cost/thermal limit states must not be treated as success. Persist checkpoints and idempotency keys; no blind replay of uncertain external side effects. User can pause/stop, inspect logs and resume. Local-only by default; cloud cost/data transfer approval and external-action permission remain explicit. Require 10 consecutive-stage device execution, failure injection and process-death recovery evidence, and independent review before final release. User’s 10-reviewer score thresholds apply; model-generated scores alone cannot substitute for real test evidence. Details: docs/onhand-auto-workflow-spec.md. Development of this feature is scheduled, not an assertion that ChatGPT or the current APK runs it autonomously.
