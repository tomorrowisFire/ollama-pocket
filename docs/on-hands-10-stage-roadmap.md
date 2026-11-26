# OnHand AI — 10단계 개발·보완계획 v1.0

기준: v0.13-B 모의평가 평균 68.8/100, 최저 60. 태블릿 구현 커밋 6f8afc392afe25f742b5fa4d8dc43d9b0aa2e0be. 현재 작업 브랜치 `dev/on-hands-ai-v012`. 실제 평가자 10명을 모집하거나 별도 AI 에이전트 10개를 독립 실행한 기록은 없으므로 A~J는 고정된 사용자 관점의 모의평가이다.

## 1. 평가 기준선과 우선순위

| 평가 관점 | 이전 | 현재 추정 | 주요 개선 요구 |
|---|---:|---:|---|
| A | 일상 활용 | 68 | 68 | 새 레이아웃에서 실제 대화 작업 완료율 및 전환 편의성 검증 전. |
| B | 문서 작성 | 60 | 60 | 문서 첨부·파싱·분석 없음. |
| C | 프로그래밍 | 76 | 76 | 기존 코드 표시·원문 복사는 보존되나 태블릿 코드 가독성 실측 전. |
| D | 업무 자동화 | 64 | 64 | MCP, 외부 도구, 워크플로 미구현. |
| E | 클라우드 AI | 77 | 77 | 기존 제공사별 대화 유지; 실제 API 검증 전. |
| F | 음성 대화 | 61 | 61 | 연속 대화·5개 언어 음성 실측 전. |
| G | 로컬 추론 | 76 | 76 | 엔진 변경 없음. 다중 기기 실측 전. |
| H | UI/UX | 66 | 67 | 측정 폭·글자 배율별 2열 안전 정책 단위 테스트 통과. 실제 화면·접근성 미검증. |
| I | 다국어 | 68 | 68 | 178개 리소스 키의 5개 언어 일치; 기존 동적 문구 번역과 실기 검증 남음. |
| J | 개인정보 | 72 | 72 | 기존 암호화·외부 전송 경계 보존; 실제 기기 통신 감사 전. |

현재 1단계 잠정 평균 68.9, 최저 60. 실제 UI/기기 검증 전에는 체감 사용성 점수 확정 금지. 가장 낮은 영역 B(문서), F(음성), D(업무 자동화), H(UI/UX)를 우선 투자 대상으로 삼는다. 번역·공통 채팅 계층을 먼저 정비하여 첨부, 음성, 클라우드, 도구가 같은 안전 경계에서 동작하게 한다.

## 2. 단계별 실행과 승인 기준

| 단계 | 범위 및 책임 관점 | 구현 목표 | 필수 승인 게이트 |
|---|---|---|---|
| 01 | 태블릿·기초 UI (H/A/I) | 자동/스마트폰/태블릿 선택, 고정 탐색·로컬 기록, 창 폭·가독성 대응, 기존 모델 카드 UI | 360/600/720/840/1024dp 이상의 가용 폭 정책, 100/113/126% 글자 및 OS 확대, 폰·태블릿 세로/가로·분할 화면, 키보드/컷아웃, 설정 영속·로컬/클라우드/광고 회귀. |
| 02 | 글로벌 언어·접근성 (I/H/F) | 한국어/영어/중국어 간체·번체/일본어, 동적 상태·오류·알림·도움말·서비스 화면, 선택 상태/접근성 | 모든 사용자 노출 문자열·숫자/날짜·복수형 리소스 검사, 5개 언어 화면 목록 100% 검수, 번역 누락 0건(고유명사·모델 ID 제외), 글자 겹침·누락 0건, 화면 읽기 및 포커스 검증. 내부 모델·RPC 식별자 유지. |
| 03 | 채팅 및 응답 품질 (A/C/H) | 수정/재생성/중지/복사, 검색·내보내기·정리, 원문 보존, Markdown/코드/표/수식 렌더링, 장문 스크롤 | 문맥 재전송·중단 시 중복/유실 없음, 데이터 마이그레이션·기존 대화 보존, 대표 렌더링/악성 입력 샘플·장문·키보드/태블릿 테스트, 자동 링크 실행 없음. |
| 04 | 파일·문서·이미지 (B/C/A/J) | 텍스트/PDF/DOCX/XLSX 등 추출, 이미지 OCR/비전은 지원 엔진만, 파일 미리보기·제거, 출처 페이지/시트 표기 | 허용 형식별 대표 샘플 비교(텍스트·표·한중일·스캔·암호화/손상), OCR/표/비전 한계 명시, 용량/메모리 제한, 실패 안전 처리, 외부 전송 별도 확인, 오프라인 지원 항목의 비행기 모드 검증. |
| 05 | 음성 대화 (F/A/I) | 오프라인 우선 STT/TTS, 명시적 음성 세션, 발화 감지·턴 전환·응답 중단, 상태/권한 표시 | 5개 언어·설치된 언어팩별 실기 인식/읽기, 권한 거부·중단·백그라운드·헤드셋·오프라인 테스트, 무음/오인식·장문 예외, 외부 음성 전송 자동 대체 금지. |
| 06 | 통합 AI/API (E/A/J) | OpenAI/Gemini/Claude/Grok/DeepSeek/OpenRouter, 지원 모델 조회, 스트리밍·비용·사용량, 선택적 PC Ollama/LM Studio, 제공사별 프로필 및 암호화 키 | 제공사별 공식 API 명세·키/모델별 승인된 테스트 또는 명시적 NOT_RUN, 오류/429/타임아웃/취소·리다이렉트 차단, 외부 전송 승인, 로컬 기록 및 키 누출 0건. 미지원 기능은 표시. |
| 07 | 로컬·협력 추론 (G/C/J) | 호스트만 GGUF, 게스트 연산 전용/QR 또는 승인된 검색, 자동 RAM 배정, 캐시·발열·중단 및 안전 전환 | CPU/Vulkan/2~3대 RPC 실측, 모델 없는 새 게스트, 호스트 모델 가중치 전송량·RAM/온도·시간·토큰 속도 기록, 연결 단절·OOM·세션 종료·기존 데이터 보존, 지원되지 않는 가속 표기 금지. |
| 08 | MCP·도구·자동화 (D/B/C/J) | 권한 기반 MCP, 웹 검색·문서/업무 API 및 템플릿, 작업/프로젝트 관리 | 읽기 기본·외부 변경/전송 별도 승인, 도구 허용목록/권한/토큰 격리, 프롬프트 주입·실패·재시도·중복 실행·로그/비밀 노출 테스트, 실제 연동 미검증은 NOT_RUN. |
| 09 | 보안·성능·광고·출시 빌드 (J/G/H/E) | 서명/업데이트/SDK 정책·16KB·AAB·권한 최소화, 추론 자원 및 UI 부하, 기존 최초 실행/다운로드/오프닝 광고 보존 | 현행 Play/AdMob 공식 정책 재확인, 네이티브 전체 16KB 호환성, 서명 검증과 업데이트 데이터 보존, 네트워크/키 감사, UMP 동의·철회·오클릭/오프라인 검증, 테스트 광고만, 운영 전환 별도 승인. |
| 10 | 통합 인수·10인 최종 평가 (A~J) | 실제 스마트폰/태블릿 과업 테스트, 성능/접근성/5개 언어/협력 추론/클라우드 통합 검증, 최종 출시 후보 | 각 평가 관점 90점 이상 및 평균 90점 이상, P0/P1 결함 0건, 필수 실기/보안/서명/AAB/회귀 게이트 PASS. 미확인 상태면 종료 금지. |

01은 현재 소스 반영 및 폭 정책 단위 테스트 단계이며, 실제 화면·기기 승인 전에는 완료 표시하지 않는다. 후속 단계는 아직 구현되지 않았다. 일정과 미래 점수는 확정하지 않는다.

## 3. 단계별 평가·반복 계약

- A~J를 변경하지 않는다. 모든 단계에서 각 평가위원의 과업·기대결과·관찰결과·근거 커밋/빌드/스크린샷·결함·보완 요청을 각각 기록한다.
- 각 관점 100점 배점: 과업 완수 30, 사용 편의 25, 신뢰성 20, 성능·자원 15, 개인정보·안전 10. 실제 확인된 개선만 점수에 반영하고 미실행은 NOT_RUN. 점수를 올리기 위한 가정·성공 판정 금지.
- 동일 테스트 기준으로 기존 기능 회귀와 신규 기능 검증 후 10인 모의평가 → P0/P1 보완 → 재평가. 해당 단계 게이트가 차단되면 다음 단계 공식 승인 금지. 최종 평균만 높고 개인 점수가 낮으면 실패.
- P0: 보안/개인정보/데이터 손실/앱 크래시/운영 광고 무단 활성화·외부 전송 등. P1: 필수 기능 작동 불능, 태블릿/다국어 접근성 중대한 결함, 서명·빌드·RPC·광고 회귀.
- 광고 3개 기존 위치와 광고 단위 유지. 최초 실행 광고 제거 금지. 광고를 보기 위해 앱 이용을 강제하지 않는다. Debug 테스트 광고, Release 운영 광고 잠금 유지. 별도 승인 없이 실제 광고 요청/Play 제출/main 병합 금지.
- 패키지 ID·서명 호환 및 기존 데이터·로컬 오프라인 CPU/GPU/RPC·언어 식별자·모델/대화 기록 보존. 실제 작업은 격리 개발 브랜치. 백그라운드 무인 실행 또는 허위 실기 평가 금지.

## 4. 현재 관찰 증빙

- Test safe Markdown parser (pure Java): SUCCESS
- Guard first-run advertisement and production lock: SUCCESS
- Test cloud scope and authenticated encryption (pure Java): SUCCESS
- Test responsive workspace policy (pure Java): SUCCESS
- Compile Java without native Ollama/Vulkan compilation: SUCCESS
- Java workflow: https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45754782081
- Native APK workflow: https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45754782085
- Previous v0.13-B APK build (2654312): PASS (45754305040).
- First-run preservation CI and cloud dialog follow-up (e45740c): Android Java PASS (45754357599).
- Tablet code static checks: 15/15; 178 keys per locale across five language sets.
- Tablet/phone real device screenshots, keyboard/rotation/split-screen, signed update, actual API/voice and RPC tests: NOT_RUN.
- Main head verified: cd12614132c6951e215be387738b0beafa4e7599; no release or live advertising enabled.

## Stage 02 preparatory scope
The program display name is now OnHand AI. Stage 01 remains pending real-device verification; Stage 02 localization source work is a candidate, not formal advancement. Do not mark remaining translated screens complete from resource count alone.
