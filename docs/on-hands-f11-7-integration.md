# F11-7 — integration candidate r001

Scope baseline (F11-2F): fe5be0a8037fa37d5c1cce77752c60946ec9216d

Application predecessor r006: 105cf97060a37021c0f329f2343e611425114647

Observed predecessor evidence at preparation: r005 FAST 46147458225 and FULL 46147458151 completed successfully, officially integrated. r006 FAST 46147978397 completed successfully, including compile, actual Whisper/TTS host and actual VLM host jobs. r006 FULL 46147978419 was still running when this review began. Failed r002 is superseded by tested r003; never use it as a successful receipt.

## Integrated non-device acceptance

- Source scope: compare exact committed trees to the fixed F11-2F baseline, reject unapproved additions, deletions, blob or mode changes. Explicitly scoped to the F11-7 checkpoint; not a claim about device data. Prior r006 diff: 63 paths, +1050/-121 lines, only authorized F11 app code/locales/tests/docs and one existing workflow/contract update.
- Host composition: exercise real model catalog/cache selection, synthetic NDJSON local response, durable pause/reopen branch, exact retained output and digest, report export, Unicode playback chunk reconstruction and stale callback rejection. Check uncertain cloud usage blocks a budgeted next request. Repeat 64 seeded multilingual fixtures. Synthetic responses do not establish actual device inference or UI wiring.
- Receipt collector: require exact FAST compile and both native voice/VLM host jobs, new cross-module and scope-rule tests; FULL requires native scope classification/check plus secure relay and package steps. New collector deliberately rejects older receipts lacking the current required steps. APK/AAB metadata is not an independently downloaded binary or verified signing proof.

## Review status

Three review perspectives (one assistant): product/UX — resource bounds and exact output handoff; engineering — preserve committed paths and data schema, test composition; QA/security — fail closed on malformed output, missing terminal events and incomplete CI evidence. These are not independent agents or device observations. No numeric ten-perspective score assigned without sufficient evidence. Target mean >=92 and every perspective >=90 remains unchanged; no promotion to FINAL or release solely from CI. All device, live API/network, actual independent review, production ad/consent, signing/update and release acceptance remain NOT_RUN or pending. Existing release approval requirements unchanged. r006 exact FAST/FULL and F11-7 exact FAST/FULL required before official integration. 
