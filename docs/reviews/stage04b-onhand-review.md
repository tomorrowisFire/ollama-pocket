# OnHand AI — Stage 04-B provisional review

Commits fb7671751f1d97cfeaa4c9079ea65bc39190b114 (Office extractor and tests) and a313dfa843e13b0665d880702177ccb9b364af19 (picker/preview). Ten fixed simulated reviewer perspectives, not actual users or independently executed agents.

| ID | Perspective | Previous | Current | Evidence / gap |
|---|---|---:|---:|---|
| A | 일상 활용 | 72 | 72 | Office 첨부 화면 반영, 실제 과업 미검증. |
| B | 문서 작성 | 63 | 65 | Word 본문과 Excel 시트명·셀 주소·수식 캐시 추출 테스트 통과; 전체 문서 분석·실제 기기 검증 전. |
| C | 프로그래밍 | 79 | 79 | 기존 채팅 및 렌더링 회귀 보존. |
| D | 업무 자동화 | 64 | 64 | 08-B 필수 명세 유지, 실행기 미구현. |
| E | 클라우드 AI | 79 | 79 | 로컬 전용 첨부, 자동 클라우드 전송 없음. |
| F | 음성 대화 | 61 | 61 | 연속 대화 미구현. |
| G | 로컬 추론 | 76 | 76 | 기존 추론 경로 보존, 첨부 실기 추론 미검증. |
| H | UI/UX | 67 | 67 | 파일 선택·읽기 전용 안내 보완, 실기 미검증. |
| I | 다국어 | 72 | 72 | 694개 키/언어, 실제 화면 검수 전. |
| J | 개인정보 | 72 | 72 | 외부 엔티티/관계 차단, 압축·XML 한도 적용, 실기 보안 감사 전. |

Mean 70.7/100; minimum 61; final gate FAIL. DOCX main-body paragraph references and XLSX workbook-order worksheet names, cell references, shared/inline strings and formula source/cached values. Read-only, no formula recalculation, macro execution, external relationship fetching, original file caching or automatic cloud transfer. UTF-8/BOM UTF-16 XML, compressed 8 MiB, expanded 16 MiB, selected part 4 MiB, 512 entries, 12 worksheets, 6000-character excerpt and existing 16000-character combined prompt limit. Native model context may be smaller. Not a complete Office parser, exact Word pages/formatting/date rendering and legacy DOC/XLS unsupported. Cached values may be stale. Real Android document picker, actual Office documents, inference and device gates NOT_RUN. PDF and images remain unimplemented. Initial Office fixture tests and existing text regression share the CI extraction step; exact assertion count is not claimed without retrieved log.

Observed:
- Previous 04-A Java and native APK PASS (45760055061, 45760055170).
- Initial Office extractor Android compilation: success (45760817086).
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
- Latest Java: https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45760882437
- Latest APK: in_progress / no conclusion https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45760882418
- Main unchanged: cd12614132c6951e215be387738b0beafa4e7599; first-run banner exact preservation checked; production advertising remains locked.
- Stage 08-B sequential automation remains mandatory and PLANNED; no final approval, real API calls, device inference or deployment.

Next: bounded PDF text extraction and source page references, then separate OCR/image candidate and user-authorized transfer controls. Preserve prior stage device gates and final all-ten >=90 requirement.