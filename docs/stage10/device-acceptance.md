# Stage 10 — device and release acceptance (NOT_RUN)

The source candidate must be assessed on the exact commit and APK/AAB SHA-256. CI, source changes and parser fixtures do not constitute a real device result. Use synthetic prompts/files, authorized test accounts, and the existing signature-compatible test installation. Do not erase the user's history/models or install incompatible signatures.

## Read-only preflight
`python scripts/collect_device_evidence.py --out device-preflight.json` (ADB authorized, one connected device; add `--serial` if necessary). This records OS/page size/display/package metadata only. It neither installs nor executes the app. Keep device identifiers and sensitive information out of shared evidence.

| Gate | Required physical evidence |
|---|---|
| api36_4k_device, api36_16k_device | Actual 4096/16384-byte page sizes, API 36, successful launch, local inference, native library loading, no crash; include APK digest and test log. |
| signed_update | Verify installed and candidate signer, install over a synthetic prior version, retain models and history; never uninstall to force an update. |
| phone_tablet_layout | 360/600/720/840/1024 dp, portrait/landscape, split screen, IME, cutout, tablet setting, no clipping. |
| five_locale_accessibility | Korean, English, Japanese, both Chinese variants; TalkBack, enlarged fonts, controls and visible text. |
| local_chat_document_code | Complete local conversation, cancel/branch/recover, synthetic PDF/OCR/Office analysis and code tasks. |
| voice_stt_tts | On-device speech permissions and recognizer, continuous turn lifecycle, stop/interrupt, language, audio routing. |
| cloud_provider_calls, pc_lan_calls | Approved synthetic data and authorized accounts, each supported provider/endpoint, model list, streaming, cancellation, error and usage. No real credentials in evidence. |
| cpu_vulkan_rpc, rpc_zero_model_guest | Same model/prompt; CPU/Vulkan/2–3 device RPC; verify native offload, model-free guest, actual tokens, transfer/peak RAM/latency/thermal. |
| rpc_disconnect_thermal | Remove guest during loading and generation, simulate warning safely, check cleanup and no duplicate/incomplete history. |
| mcp_manual_permissions | Authorized trusted server, supported and unsupported schemas, revoke, changed definition, interruption, no unauthorized calls. |
| workflow_10_steps, workflow_resume_cancel | 10 steps with one start, no manual next; deterministic validators, failed-step retry bounds, pause/cancel, process death, recovery and preserved completed steps. |
| network_privacy_sdk | Inspect SDK data flows, credential storage, LAN and loopback routes, transport approvals, no unauthorized data disclosure. |
| test_ads_consent | Test ads only; onboarding, download, app open, withdrawal, rotation, no-fill/offline; usable app without ad interaction. |
| play_policy_review | Confirm actual Play requirements and declarations, privacy/data safety, FGS, content rating, signing and developer account. No actual submission without approval. |

## Final gate
Copy evidence-template.json into a private evidence folder. Fill the exact candidate commit, each gate status/commit and relative evidence file path + SHA-256; attach all ten rubric reviews (30 task / 25 usability / 20 reliability / 15 performance / 10 privacy) and reviewed defect register. The validator checks completeness, file hashes and scores; it cannot validate truth, independence, or completeness of the attached observations. Run:

`python scripts/stage10_gate.py path/to/evidence.json --report path/to/gate-status.json`

Exit 0 only means READY_FOR_MANUAL_APPROVAL, never automatic release. Final release requires manual evidence inspection, all ten scores >=90, mean >=90, no unresolved P0/P1, and separate user approval. Template deliberately blocks. CI uses only synthetic fixtures for gate code. Do not put personal device data, keys or private conversations in the public repository.
