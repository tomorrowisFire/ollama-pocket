# OnHand AI — Stage 04-C provisional review

Commits db6e2ae58c23002c0e0a9874245aa94117bbc2a9 (page policy), f893c2e98755790bbbfce2cd59734f318711ce74 (isolated native worker), 2f013bd3ad5e9bada792100a820376e8b95415db (picker/preview). Ten simulated reviewer perspectives, not ten actual reviewers or independently executed agents.

| ID | Perspective | Previous | Current | Evidence / gap |
|---|---|---:|---:|---|
| A | 일상 활용 | 72 | 72 | PDF 첨부 후보 구현, 실제 과업 미검증. |
| B | 문서 작성 | 65 | 66 | 페이지 인덱스·발췌 한도·텍스트 미검출 정책 테스트와 격리 서비스 컴파일 PASS. Android 15+ 실기 추출 미검증. |
| C | 프로그래밍 | 79 | 79 | 기존 채팅·코드 렌더링 유지. |
| D | 업무 자동화 | 64 | 64 | 08-B 필수 계획 유지, 실행기 미구현. |
| E | 클라우드 AI | 79 | 79 | PDF 자동 클라우드 전송 없음. |
| F | 음성 대화 | 61 | 61 | 연속 대화 미구현. |
| G | 로컬 추론 | 76 | 76 | 기존 엔진 보존, 문서 추론 실측 미실행. |
| H | UI/UX | 67 | 67 | 첨부 미리보기 후보, 실제 기기 검수 없음. |
| I | 다국어 | 72 | 72 | 700개 키/언어, 실제 화면 검수 전. |
| J | 개인정보 | 72 | 72 | 격리 서비스, 읽기 전용 FD, 입력·시간 한도 구현. 실제 격리와 캐시 잔여 검사 미실행. |

Mean 70.8/100; minimum 61; final gate FAIL. Android 15/API 35+ PDF text extraction source candidate, based on Android PdfRenderer.Page.getTextContents, no new dependency. First 12 physical pages, max 6000-character excerpt and 8 MiB input, separate non-exported isolated process, read-only seekable descriptor, 35-second native watchdog and 45-second client IPC wait. PDF text order may differ from visual order; no OCR, image/table reconstruction, protected-file extraction, full-document analysis or support on Android below 15. Temporary private source is unlinked before native parsing and cleanup attempted; abnormal interruption during copying can leave residue. I/O duration and native memory are not fully bounded by the IPC deadline. No automatic cloud upload. Actual Android PDF worker isolation, real documents, provider, rotation, parser timeout and cache cleanup NOT_RUN. Stage 08-B remains mandatory and unimplemented.

Observed:
- PdfExcerptTest PASS: 21 assertions (45761606014).
- Native isolated-service Android compile success (45761705440).
- Previous 04-B integrated Java and APK PASS (45760882437, 45760882418).
- Test safe Markdown parser (pure Java): SUCCESS
- Test bounded rich answer blocks and simple formula rendering: SUCCESS
- Guard first-run advertisement and production lock: SUCCESS
- Test cloud scope and authenticated encryption (pure Java): SUCCESS
- Test responsive workspace policy (pure Java): SUCCESS
- Test diagnostic formatting and advisor display data (pure Java): SUCCESS
- Test cooperative plan display data (pure Java): SUCCESS
- Test local chat branching and completed-turn context: SUCCESS
- Test local conversation search and export helpers: SUCCESS
- Test chat title and pin ordering: SUCCESS
- Test bounded local attachment text extraction: SUCCESS
- Audit direct Hangul UI literals: SUCCESS
- Verify OnHand AI brand and locale resource parity: SUCCESS
- Compile Java without native Ollama/Vulkan compilation: PENDING
- Latest Java https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45761770468
- Latest APK in_progress / no conclusion https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45761770421
- Main unchanged: cd12614132c6951e215be387738b0beafa4e7599; original first-run ads remain, production requests locked. No device execution, live ads, real API calls or deployment.

Next: offline image OCR candidate, scanned PDF handling, then attachment context and transfer safeguards; check all prior actual-device gates. Preserve mandatory 08-B and individual/mean 90+ exit rule.