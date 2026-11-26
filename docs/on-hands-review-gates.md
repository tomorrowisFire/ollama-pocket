# On Hands AI — independent-perspective review and release gate

## User-directed review contract
Each improvement stage must be followed by ten independent-perspective *simulated* reviews. They are not ten real users or independently executed agents unless separately demonstrated. No fabricated hands-on evaluations, test passes, or approval claims.

Baseline: v0.12.0 dev commit d5431da0e17ca25c3dcb7a3d9f85f28e6405aae7. Prior mock panel scores A–J: 61,58,72,64,69,60,76,54,62,67; mean 64.3/100. Baseline is a heuristic, not a measured product KPI. Java compile run 45752880517 succeeded, existing RPC/security/unit checks succeeded in run 45752880580, native APK/device tests incomplete at time of record.

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
