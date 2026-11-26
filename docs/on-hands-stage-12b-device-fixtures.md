# 12-B real-device test protocol (frozen fixtures, NOT_RUN)

Applies the already agreed B4–B7 KPIs without lowering any threshold. Use only synthetic data. Prepare files before running and freeze a manifest containing SHA-256 of the input files, exact APK/application commit, device/OS, installed model tag and quantization, CPU mode, app/goal locale, and test order. At least Android 15 for the PDF case. Use the same installed model throughout if feasible; document and separately report any change. Do not select a model based on observed test scores.

## Ten task cases

Each row is one real-device run; use the corresponding goal language and the same ten fact IDs F01–F10 below. For a single file, place all ten facts in that file. For a three-file case, split F01–F04, F05–F07, F08–F10 across the listed files, in order. Use UTF-8 for text/CSV; CSV columns are fact ID and value, DOCX paragraphs are one fact per paragraph, XLSX has ID/value in columns A/B (literal text, no formulas); PDF contains selectable ASCII text, one fact per line. Use only a locally created standard-format file, not a renamed text file. Preserve exact values. Manually verify the extracted preview before starting.

| ID | App and goal locale | File format(s) |
|---|---|---|
| T01 | ko | DOCX |
| T02 | ko | TXT / CSV / XLSX |
| T03 | en | text PDF |
| T04 | en | TXT / DOCX / CSV |
| T05 | ja | XLSX |
| T06 | ja | TXT / CSV / DOCX |
| T07 | zh-Hans | TXT |
| T08 | zh-Hans | DOCX / CSV / XLSX |
| T09 | zh-Hant | CSV |
| T10 | zh-Hant | TXT / DOCX / XLSX |

For case Tnn, use unique case-specific values: F01=CASE-Tnn; F02=2031-04-15; F03=ALPHA-nn; F04=BETA-nn; F05=137; F06=284; F07=421; F08=7.5%; F09=REVIEW-PENDING; F10=SOURCE-SYNTHETIC. Insert one literal line per fact, such as F01: CASE-T01. For the three-file cases add a distractor line, clearly labeled NOT-A-FACT: CASE-OTHER, to the second file. This is a simple extraction/coverage baseline, not a representative full-document reasoning benchmark. Additional long and difficult document samples may supplement but not replace the fixed ten cases. Freeze files and reference checklist before collecting results.

Goals:
- ko: F01부터 F10까지 각 항목의 값을 정확히 나열하고, 가능한 경우 구간 출처 ID를 대괄호로 표기하세요. 없는 정보는 추측하지 마세요. NOT-A-FACT 항목은 제외하세요.
- en: List the exact values of F01 through F10, with bracketed excerpt IDs when possible. Do not invent missing values. Exclude NOT-A-FACT.
- ja: F01からF10までの値を正確に列挙し、可能なら角括弧付きの区間IDを示してください。不明な値を推測せず、NOT-A-FACTを除外してください。
- zh-Hans: 准确列出F01至F10的值，尽可能标注方括号来源片段ID。不得猜测缺失值，排除NOT-A-FACT。
- zh-Hant: 準確列出F01至F10的值，盡可能標註方括號來源片段ID。不得猜測缺失值，排除NOT-A-FACT。

For each case record per-fact correct/incorrect/omitted, unsupported claims, unflagged unknown bracketed IDs, whole-file coverage claims, and whether the synthesis completed. Pass only with >=9/10 facts correct, no unsupported factual claim, no misleading whole-file assertion and no unflagged unknown bracketed ID. B4 requires >=9/10 passing runs and zero prohibited whole-file or ID failures in all ten. Use original files and report ranges, not a model grading itself. No result recorded yet.

## B5: five fault cases
F1 cancel during a running excerpt after at least one note; F2 controlled thermal or low-memory condition (use safe device instrumentation, do not deliberately overheat hardware); F3 stop local server mid-run; F4 induce the existing estimated synthesis context limit with a controlled fixture; F5 select an unsupported synthetic file. For each compare before/after model/history inventory, verify correct failure indication, retained completed in-screen notes where applicable, and no fabricated completed synthesis. If a condition cannot be induced safely or reliably, mark NOT_RUN. No test-hook bypass in production.

## B6/B7 evidence
For each device run capture the local report (attempt count, elapsed ms, planned/completed range, synthesis and warnings); independently capture loopback request count/map operations and elapsed time, and an authorized network trace distinguishing document provider reads, model downloads and ad SDK from inference. Record safety cleanup separately. In-app metrics alone are not proof of network isolation or deadline behavior. For each locale record screenshots of source preview, confirmation, progress, success or failure report, and first-run test ad; test narrow and tablet windows if available. Confirm no blocking clipping, no misleading status and no production ad requests. If external monitoring or a physical device is unavailable, do not mark PASS.

## Result ledger
Candidate SHA | APK SHA-256 | case ID | model | device/OS | locale | 10 fact checks | status | actual request counts | elapsed | transfer observations | report/screens/log paths | reviewer | unresolved defect. All cases are NOT_RUN at creation. Keep sensitive device logs private; only synthetic/redacted evidence may be committed.
