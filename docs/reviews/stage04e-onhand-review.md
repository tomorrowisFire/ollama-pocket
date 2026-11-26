# OnHand AI — Stage 04-E provisional review

Commits 80db6f4 (document windows), cf7382b (PDF range), 3b88b55 (picker and attachment management), d71054079c7439aec55c98067c2e16cc0857236b (transfer review). Ten fixed simulated reviewer perspectives, not ten actual reviewers or independent agent runs.

| ID | Perspective | Previous | Current | Evidence / gap |
|---|---|---:|---:|---|
| A | 일상 활용 | 72 | 72 | 선택 구간·첨부 제거 UI 반영, 실제 과업 미검증. |
| B | 문서 작성 | 67 | 69 | DocumentWindowTest 9 assertions 및 PDF 정책 24 assertions 통과, 실제 장문 추론 및 전체 문서 요약 미검증. |
| C | 프로그래밍 | 79 | 79 | 기존 채팅·코드 렌더링 회귀 유지. |
| D | 업무 자동화 | 64 | 64 | 08-B 필수 계획 유지, 실행기 미구현. |
| E | 클라우드 AI | 79 | 79 | 전송 문맥 표시 구현; 실제 API 요청 미검증. |
| F | 음성 대화 | 61 | 61 | 연속 음성 대화 미구현. |
| G | 로컬 추론 | 76 | 76 | 기존 추론 경로 유지, 실제 기기 성능 미검증. |
| H | UI/UX | 67 | 67 | 첨부 구간 및 제거 UI 후보, 실기 검증 전. |
| I | 다국어 | 72 | 72 | 728개 리소스/언어, 언어별 실제 화면 미검증. |
| J | 개인정보 | 72 | 73 | 실제 문맥 승인 지문 정책 테스트 통과; 기기별 네트워크·사용자 승인 UX 및 SDK 감사 미실행. |

Mean 71.2/100; minimum 61; final gate FAIL. Selectable text/Office windows 1–256, nominal stride 5999 UTF-16 units, bounded extraction and source range metadata. PDF physical starting page, up to 12 pages/6000 characters. Original file may change between separate selections. This is manual segment analysis, not automatic document-wide summarization, retrieval index or guaranteed document coverage. OCR scope remains prior stage. Last exact unedited inserted suffix can be removed in the same Activity lifetime. User-approved clipboard copy; no automatic cloud attachment upload. Cooperative inference review is required per request, with scope/model/route/payload approval fingerprint; connected guest set may change. Cloud message review includes selected conversation text, not full HTTP metadata. Existing local histories, original first-run advertisement, production ad lock and package ID preserved. No real device, API, OCR, or network/privacy execution. No claim of device acceptance.

Observed:
- Prior 04-D integrated Java and APK PASS (45762187850, 45762187867).
- DocumentWindowTest PASS: 9 assertions; PdfExcerptTest PASS: 24 assertions (45762825083).
- Stage 04-E3 Java PASS (45762825083).
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
- Compile Java without native Ollama/Vulkan compilation: IN_PROGRESS
- Latest Java https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45762959993
- Latest APK in_progress / no conclusion https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45762959997
- Main unchanged cd12614132c6951e215be387738b0beafa4e7599; stage 01–04 device gates NOT_RUN. Mandatory 08-B continuous task automation remains PLANNED.

Next source candidate: Stage 05 continuous voice conversation (STT/TTS lifecycle, language, pause/cancel, interruption, explicit transfer approval). Stage 04 actual device acceptance and automatic full-document workflows remain open. Keep all prior device gates, first-run ads, 08-B mandatory scope and individual/mean 90+ final exit rule.