# Stage 12-B — fixture-pack preparation and evidence audit

Three-role meeting (simulated, one assistant): A requests reproducible user tasks and a simple goal sheet; B requests deterministic standard-format input generation and file hashes without changing the application; C requires frozen answers, negative fixture, all results initialized NOT_RUN, and same-candidate CI evidence. Decision: preserve the already frozen B1–B8 thresholds, keep application candidate 1baac26eac778aa4a17cf529c874c4f8cb411db2, and prepare the actual test inputs instead of making another speculative application change.

## Verified application CI
- Java run 45794478365: SUCCESS, including all 176 document batch/coverage/failure-policy assertions in job 139821500575, first-run ad guard, locale parity and Android Java compile. B1 PASS and deterministic B3 suite PASS for this candidate; this does not certify actual inference or all imaginable malformed IDs.
- Native run 45794478381: in progress at inspection, B2 pending. No previous-commit evidence substituted.

## Generate test kit
From the repository root:
    python scripts/prepare_stage12b.py --candidate 1baac26eac778aa4a17cf529c874c4f8cb411db2 --out OnHandAI_12B_TestKit.zip

No dependency installation, network connection, device modification or test execution is performed by this generator. Output contains 20 standard-format synthetic input files (TXT, CSV, DOCX, XLSX, text PDF), one unsupported-file fixture, fixed goals, expected answers and a NOT_RUN ledger. The generated local package was checked with independent format readers: 10 cases / 20 files, all 100 expected ID/value presence checks, file SHA-256 checks, one negative file, and byte-identical regeneration. PDF text extraction and page rendering succeeded. These checks verify fixtures, not OnHand AI results. The kit generated locally had ZIP SHA-256 8bdad89c31524f242fa70455896b92dc612259f0191a0d10042d252b7cdde31b and manifest SHA-256 39cc29160429a7c7aaec2fe4b4591916a844174e0aa549082150a94397bdad55. The repository generator is provided for reproduction; compare its generated hash with these values before testing.

## Gate
B4–B7 NOT_RUN: real Android device, installed model, authorized runtime evidence and actual reviewer observations not available in this session. B8 BLOCKED pending actual evidence, ten-perspective final stage assessment, and defect review. No new app code, no KPI reduction, no score uplift, no 12-C implementation. This is a process-only commit; the application candidate SHA above remains fixed.
