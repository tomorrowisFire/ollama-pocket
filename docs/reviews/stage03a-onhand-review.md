# OnHand AI — Stage 03-A provisional review

Implementation 2aa8c55f1aeb2ca67ef2da472812ee679657a6f2; follow-up e74d7dacd767374feeebecc8574230db894ba03a. Ten fixed simulated user perspectives; not ten actual users or separately executed agents.

| ID | Perspective | Previous | Current | Evidence / gap |
|---|---|---:|---:|---|
| A | 일상 활용 | 68 | 70 | 로컬 질문 수정·재생성 분기 및 완료된 문맥 단위 테스트 통과, 초안 복구 UI는 미검증. |
| B | 문서 작성 | 60 | 60 | 파일 첨부와 문서 분석 미구현. |
| C | 프로그래밍 | 76 | 77 | 완료 신호 검증과 중단 문맥 차단 구현, 실제 생성 검증 전. |
| D | 업무 자동화 | 64 | 64 | 도구 연동 미구현. |
| E | 클라우드 AI | 77 | 77 | 클라우드 패널 변경 없음; 실제 API 미검증. |
| F | 음성 대화 | 61 | 61 | 연속 대화 및 실기 미검증. |
| G | 로컬 추론 | 76 | 76 | CPU/GPU/RPC 경로 보존; 실기 추론 미검증. |
| H | UI/UX | 67 | 67 | 편집·재생성 UI 구현, 실제 화면 검증 없음. |
| I | 다국어 | 72 | 72 | 637개 리소스, 신규 화면 실기 미검증. |
| J | 개인정보 | 72 | 72 | 로컬 초안·기록 분리, 광고 잠금 회귀 유지; 기기 검증 전. |

Mean 69.6/100; minimum 60; exit gate FAIL. Local conversation branching, bounded completed-pair context, stream terminal validation, draft recovery and snapshot guards are implemented. Historical chats are preserved; no silent 32-message trim. New storage bound 256 records/512000 serialized chars. Latest source/CI only, not real-device verification. Actual UI persistence, rotation, cancellation and CPU/GPU/RPC execution require device evidence. Cloud branching and advanced rendering/export are not implemented in this stage.

Evidence: LocalChatTurnsTest PASS: 16 assertions. Initial Android compile run 45757641187 success. Follow-up source checks 6/6 PASS. Latest CI:
- Test safe Markdown parser (pure Java): SUCCESS
- Guard first-run advertisement and production lock: SUCCESS
- Test cloud scope and authenticated encryption (pure Java): SUCCESS
- Test responsive workspace policy (pure Java): SUCCESS
- Test diagnostic formatting and advisor display data (pure Java): SUCCESS
- Test cooperative plan display data (pure Java): SUCCESS
- Test local chat branching and completed-turn context: SUCCESS
- Audit direct Hangul UI literals: SUCCESS
- Verify OnHand AI brand and locale resource parity: SUCCESS
- Compile Java without native Ollama/Vulkan compilation: PENDING
- Java: https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45757742172
- APK: queued / no conclusion https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45757742043
- Stage 02-E Java and APK PASS (45757182990 and 45757183011); Stage 01/02 real device gates still NOT_RUN.
- Main unchanged: cd12614132c6951e215be387738b0beafa4e7599; AdSupport and onboarding placement preserved; no real API requests, live ads, device execution or deployment.
Next: chat rendering, export and search improvements; device gates remain mandatory.