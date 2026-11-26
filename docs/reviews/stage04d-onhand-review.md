# OnHand AI — Stage 04-D provisional review

Commits 823319ff8dd7d29bfeab21c055df16bdd8e8fdaf (bundled OCR engine and policy), 5544e179a96439b3a00258b197c40846eb45100b (isolated PDF raster), 5ab8b209b559ad9a03f911ac8eea19c67ff9f6ea (picker/preview). Ten fixed simulated reviewer perspectives, not actual reviewers or separate agents.

| ID | Perspective | Previous | Current | Evidence / gap |
|---|---|---:|---:|---|
| A | 일상 활용 | 72 | 72 | 이미지 첨부와 OCR UI 후보, 실제 과업 미검증. |
| B | 문서 작성 | 66 | 67 | OCR 입력/발췌 정책 11 assertions PASS, 이미지 OCR SDK 컴파일 PASS; 실제 인식률 및 스캔 PDF 처리 미검증. |
| C | 프로그래밍 | 79 | 79 | 기존 채팅 및 코드 렌더링 회귀 보존. |
| D | 업무 자동화 | 64 | 64 | 08-B 필수 계획 유지, 실행기 미구현. |
| E | 클라우드 AI | 79 | 79 | OCR 결과 자동 클라우드 전송 없음. |
| F | 음성 대화 | 61 | 61 | 연속 대화 미구현. |
| G | 로컬 추론 | 76 | 76 | 기존 추론 경로 보존, OCR 성능·발열 미실측. |
| H | UI/UX | 67 | 67 | 언어/페이지 선택 및 미리보기 코드 반영; 실기 접근성 미검증. |
| I | 다국어 | 72 | 72 | 712개 리소스/언어; 4문자권 OCR 실제 인식 및 화면 미검증. |
| J | 개인정보 | 72 | 72 | PDF 격리 및 읽기 전용 픽셀 전달, 명시적 삽입; Google SDK 진단·네트워크·캐시 실제 감사 전. |

Mean 70.9/100; minimum 61; final gate FAIL. Google ML Kit bundled Latin/Korean/Chinese/Japanese 16.0.1 models; image OCR PNG/JPEG/WEBP, 8 MiB source, <=2048 px each side and <=4M decoded pixels. Explicit single selected PDF page OCR requires Android 15+; PDF native rendering isolated, pixels transferred through bounded read-only shared memory, recognition uses app process. 30-second OCR result wait is not cancellation of native SDK work; input retained until SDK completion. User chooses script, page and explicit insertion. No automatic cloud AI upload. OCR is not image understanding, table reconstruction or full-document analysis. SDK diagnostics, model accuracy, airplane mode, memory/thermal behavior, shared-memory IPC, EXIF orientation, APK size and every native library 16KB alignment remain actual-device/release gates. Google SDK is an additional dependency; disclosure and actual network audit required. First-run ad placement, production lock, histories and native inference unchanged. 712 keys per locale. No real OCR device test or paid API call. Stage 08-B mandatory automatic workflow remains PLANNED.

Observed:
- OcrPolicyTest PASS: 11 assertions; initial image OCR Android Java compile PASS (45762025836).
- Prior 04-C integrated Android Java PASS (45761770468).
- PDF raster intermediate Java: completed / success (45762082538).
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
- Latest Java https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45762187850
- Latest APK in_progress / no conclusion https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45762187867
- Main unchanged cd12614132c6951e215be387738b0beafa4e7599; real phone/tablet, offline OCR, actual PDF page inference, stage 01–04 device gates NOT_RUN.

Next: stage 04-E attachment context/transfer safeguards, explicit user-approved cloud attachment if needed, chunking/long document and real-device acceptance plan; audit SDK packaging and resolve build regressions before next source candidate. Preserve mandatory 08-B and final all ten >=90 gates.