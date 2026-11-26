# OnHand AI — Stage 04-A provisional review

Commits fb0242975bec094601305a3231be6e66b82acff0 (text extractor/tests) and ec10d2a5f1f154d756bd59e6d5c7bb4b4eef0fb9 (Android picker/preview). Initial combined Office implementation attempt disconnected before commit; the branch was checked and only the successful smaller text changes are included. Word/Excel/PDF/image are not implemented yet. Ten simulated reviewer perspectives, not actual reviewers or independently executed agents.

| ID | Perspective | Previous | Current | Evidence / gap |
|---|---|---:|---:|---|
| A | 일상 활용 | 72 | 72 | 로컬 첨부 UI 구현; 실제 파일 선택·과업 미검증. |
| B | 문서 작성 | 61 | 63 | 텍스트 발췌, 다국어·인코딩·용량·원문 테스트 통과. Word/Excel/PDF/이미지 미구현. |
| C | 프로그래밍 | 79 | 79 | 기존 기능 유지; 파일 기반 코딩 과업 미검증. |
| D | 업무 자동화 | 64 | 64 | 08-B 계획 유지, 자동진행 실행기 미구현. |
| E | 클라우드 AI | 79 | 79 | 첨부 자동 전송 없음. 클라우드 첨부 미구현. |
| F | 음성 대화 | 61 | 61 | 연속 대화 미구현. |
| G | 로컬 추론 | 76 | 76 | 기존 엔진 경로 유지, 첨부 추론 실측 없음. |
| H | UI/UX | 67 | 67 | 미리보기·편집 UI 후보; 실기 미검증. |
| I | 다국어 | 72 | 72 | 693개 키/언어; 실제 화면 미검증. |
| J | 개인정보 | 72 | 72 | 사용자 선택 URI, 명시적 삽입, 원본 캐시 없음; 실제 기기 검증 전. |

Mean 70.5/100; minimum 61; final gate FAIL. Text/CSV/TSV/Markdown/JSON/LOG extraction limited to UTF-8 or BOM UTF-16, 1 MiB file, 6000-character preview and existing 16000-character combined prompt. This is excerpt analysis, not full-document reasoning. The model token budget may be smaller. File is not copied into app storage; accepted excerpt becomes local composer/draft/history data and may be included in export/context. Document provider may fetch over network; selected cooperative inference may process across trusted nodes. No automatic cloud upload. No broad file permission added. Existing ads/engines/history and Stage 08-B requirement retained.

Observed:
- AttachmentTextTest PASS: 12 assertions; initial extractor Android compile PASS (45759978714).
- Prior 03-C latest Android Java and APK PASS (45759280411,45759280424).
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
- Latest Java https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45760055061
- Latest APK in_progress / no conclusion https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45760055170
- Main unchanged: cd12614132c6951e215be387738b0beafa4e7599. No live ad activation, real device inference, API call or deployment.
- Actual file picker, preview, provider behavior, Android lifecycle, model response and prior stage 01–03 device gates NOT_RUN.

Next: 04-B bounded DOCX/XLSX extraction with source references, zip expansion/entity protection; PDF and image candidates separately. Maintain user-required 08-B automatic workflow and all ten scores >=90 before exit.