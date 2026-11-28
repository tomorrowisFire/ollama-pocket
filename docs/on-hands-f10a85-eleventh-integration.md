# F10A85 — eleventh focused integration

Parent F10A84 e4c0834e8f38c42fd7c1d49ea0b0cefde639ee30: FAST 46110037847 and FULL 46110037842 both passed.

- Local CPU/GPU/RPC interactive text: reject malformed UTF-8, invalid Unicode/control fragments, unsupported tool/action/refusal payloads, multi-choice or nonzero-index SSE and invalid completion reasons before forwarding. Preserve legitimate stop/length and legacy omitted reason, ordinary layout whitespace and noninteractive protocol handling. Invalid historical context is skipped, not deleted.
- Voice: bound partial preview scan at 4096 UTF-16 units, enforce validated exact source before playback cursor creation. Preserve reviewed offsets and language settings.
- Project and knowledge transactions: compare no-change candidate against original read bytes, reject same-revision mutation, preserve interrupt state after failed mutation/write/worker. Write-uncertain outcome remains uncertain, no claim of rollback.
- Regression fixtures exercise text and stream guards, voice limits and interrupted workers, and mutation with unchanged revision. Preserve all prior scopes and release/ads/billing rules.

Integrated in a single atomic commit. Exact FAST/FULL validation pending, actual physical tests and independent evaluator gate deferred. No measured speed or quality score increase claimed.
