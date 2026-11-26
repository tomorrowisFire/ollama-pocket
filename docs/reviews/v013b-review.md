# v0.13-B — provisional ten-perspective review

Implementation: 2654312da6c098f943afb304439642b51c465870; follow-up ad-preservation CI and credential dialog cleanup: e45740cebf70ac7958151353785886ca0a84f69b. These are ten simulated reviewer perspectives, not actual user testing or ten independently executed agents.

| Reviewer | Focus | Previous | Current estimate | Evidence and limitation |
|---|---|---:|---:|---|
| A | 일상 활용 | 65 | 68 | Main chat can switch local/cloud; no real-device usability test. |
| B | 문서 작성 | 60 | 60 | No document ingestion added. |
| C | 프로그래밍 | 76 | 76 | No additional coding-specific evidence; prior Markdown support preserved. |
| D | 업무 자동화 | 64 | 64 | MCP/tools not added. |
| E | 클라우드 AI | 71 | 77 | Multi-turn cloud-only session scope, six existing endpoints; pure Java history tests passed. Real provider calls NOT_RUN. |
| F | 음성 대화 | 61 | 61 | Prior voice implementation preserved; no new voice evidence. |
| G | 로컬 추론 | 76 | 76 | No inference engine changes; prior native APK succeeded, latest device tests NOT_RUN. |
| H | UI/UX | 62 | 66 | Embedded cloud panel, first-run banner preserved; no screenshots or actual device interaction. |
| I | 다국어 | 67 | 68 | 164 shared keys for five locales, partial legacy Korean text remains. |
| J | 개인정보 | 68 | 72 | AES-GCM tests passed, explicit save and per-provider isolation; actual Android Keystore and traffic audit NOT_RUN. |

Mean: 68.8/100; minimum 60. Final gate FAIL (both score and actual quality gates unmet). First-run banner preservation is a mandatory requirement, not a reason to award additional points or enable live ads.

Observed: first-run restoration commit 01217ce94a24971867ef363d0a19668c123d3f23 Java compile PASS (45754062246); 9 first-run static checks and 9 subsequent combined-stage static checks PASS. Cloud scope/authenticated encryption and Markdown pure-Java tests PASS in 45754305043; Android compile in progress at inspection. Latest follow-up CI run 45754357599 in progress; ad-regression check registered, not yet observed passing. Native build 45754305040 in progress. Previous v0.13-A native APK run 45753679498 SUCCESS. No authorized provider requests or ad impressions were exercised, no Android Keystore/device tests, no AAB/submission/approval.

Next: verify newest commit compilation, APK and practical UI behavior; finish localization, supported file analysis, provider compatibility, actual device performance and privacy; no merge or live ad activation.
