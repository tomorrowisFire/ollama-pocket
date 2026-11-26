# OnHand AI — Stage 10-F 종합 회귀 검수 (잠정)

검토 대상 앱 소스: `c560d4b02db57682d6828bfda4751ece21ca5659` (10-E). 10-F는 검수 도구와 문서 변경이며 앱 소스는 변경하지 않는다. 서로 다른 커밋의 APK, CI 또는 기기 결과를 하나의 승인 증빙으로 합치지 않는다.

## 확인된 증빙

- 10-E Java CI [45784969406](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45784969406): SUCCESS. 컴파일 BUILD SUCCESSFUL. 10-D에서 Maven 429로 미실행이었던 벤치마크 정책 35개도 통과.
- 동일 커밋 10-E native [45784969284](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45784969284): secure-relay-unit-test SUCCESS, cooperative-apk IN_PROGRESS. 최신 APK/AAB 및 16KB 결과 미확정.
- 이전 앱 소스 e9ad902a5e1c2cf61631960554d7dcb1d9f97e55 [45783972991](https://github.com/tomorrowisFire/ollama-pocket/actions/runs/45783972991): native APK, unsigned QA AAB, 16KB 정적 검사 및 증빙 업로드 SUCCESS. 이전 커밋 결과이므로 10-E의 최신 바이너리 승인으로 사용 금지. Stable update artifact step SKIPPED.
- 10-C~10-E Java 정책: workflow 32 + MCP 16 + library 23 + journal 28 = 99 checks. Local inference 15; benchmark scope 35; privacy 32; RPC socket ownership 12. WorkspaceLayoutPolicyTest 2,088 assertions. First-run banner/production lock, five-locale format parity, application ID guard all SUCCESS.
- 최종 gate 템플릿은 CI에서 BLOCKED 35를 반환(빈 증빙 회귀 확인). 이 수치는 실제 결함 건수가 아니다.

## 10개 고정 관점 모의평가

실제 10인 평가·독립 에이전트 실행이 아니다. 소스 및 확인된 회귀 결과에 대한 한 번의 분석적 추정으로, 이전 점수 73.8와 비교한다. 실제 기기 체감·성능·정확도·독립 보안의 미실행은 점수 확정 근거가 아니다. 배점은 과업 30, 편의성 25, 신뢰성 20, 성능 15, 안전 10. 다음 수치는 제품 KPI가 아니며 최종 evidence.json의 REVIEWED 상태에 입력하지 않는다.

| ID | 관점 | 이전 | 과업 | 편의 | 신뢰 | 성능 | 안전 | 잠정 | 검증 근거 | 미검증/결함 |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| A | 일상 활용 | 72 | 24 | 18 | 16 | 9 | 7 | 74 | 채팅 분기·입력 초안·도구 메뉴의 회귀 검사 통과. | 실제 대화 완수율, 키보드와 장문 스크롤, 오프라인 전환. |
| B | 문서 작성 | 69 | 22 | 18 | 14 | 10 | 8 | 72 | 23개 첨부 검사, Office 16개, PDF 24개, OCR 11개, 구간 9개 통과. | 실제 문서 표·수식·스캔 정확도, 장문 전체 분석과 HWP 미지원. |
| C | 프로그래밍 | 79 | 25 | 20 | 16 | 12 | 7 | 80 | Markdown·코드 블록·분기·로컬 스트림 검사 통과. | 코드 생성의 기능 정확성, 실제 파일/테스트 도구 연계. |
| D | 업무 자동화 | 71 | 23 | 18 | 16 | 10 | 8 | 75 | 워크플로·MCP 입력·라이브러리·이벤트 정책 99개 검사 통과. | 실제 10단계·프로세스 종료 복구, 객관적 외부 작업 검증 및 도구 자동 실행 미지원. |
| E | 클라우드 AI | 85 | 26 | 21 | 17 | 12 | 9 | 85 | 기존 클라우드 문맥·스트림·비용·키 격리 검사 유지. | 6개 제공사 실제 인증·호출·사용량 확인, PC 연계 및 비용 검증. |
| F | 음성 대화 | 69 | 22 | 17 | 15 | 10 | 8 | 72 | 초안 보존 및 순차 TTS 콜백 정책 검사 통과. | 실기기 5개 언어 인식률·지연·끊김·오프라인 언어팩. |
| G | 로컬 추론 | 78 | 25 | 18 | 17 | 12 | 8 | 80 | 벤치마크 스코프 35개·안전 및 협력 정책·RPC 암호화 테스트 통과. | 동일 모델 CPU/Vulkan/RPC 속도·RAM·발열 실측, 모델 없는 게스트. |
| H | UI/UX | 67 | 22 | 17 | 15 | 10 | 7 | 71 | 폭 정책 2,088개 검사, 도구 통합·입력창 구성·48dp 후보 코드 컴파일 통과. | 실제 폰/태블릿 화면, 확대, 회전, 분할, TalkBack, 디자인 품질. |
| I | 다국어 | 72 | 23 | 18 | 15 | 10 | 7 | 73 | 5개 언어 키와 포맷 인수 일치, 동적 상태 및 오류 리소스 검사 통과. | 언어별 실제 화면 잘림·번역 적정성 및 STT/TTS 지원. |
| J | 개인정보 | 76 | 23 | 17 | 17 | 13 | 9 | 79 | 32개 개인정보·캐시 검사, 12개 소켓 정책 및 암호화 테스트 통과. | 실제 SDK/LAN/loopback 데이터 흐름, 백업·복구·서명·독립 보안 검수. |

평균 **76.1/100**, 이전 대비 +2.3점, 최저 71점, 90점 이상 0/10. 공식 평가는 NOT_RUN, 공식 출시 게이트 BLOCKED.

## 주요 잔여 차단 사항

1. 동일 커밋 native APK, QA AAB, 실제 ELF/ZIP 검사 보고서 및 업데이트 서명 증빙 확인.
2. 서명 호환 기기에서 기존 데이터와 모델을 보존한 업데이트. 테스트용 별도 프로필 사용. 강제 삭제·재설치 금지.
3. 실제 스마트폰·태블릿에서 화면/IME/접근성/5개 언어, 문서 및 음성 정확도, 로컬 CPU/GPU/RPC, 실제 API/MCP, 10단계 자동진행과 실패·프로세스 종료 복구 검증.
4. 기능 한계: 자동진행은 foreground CPU 텍스트 구조 검증만 지원. 외부 작업 결과 검증/도구 자동 실행은 미구현. 문서 분석은 부분 발췌 중심이며 전체 파일 분석 정확도 미보장. 음성은 기기 언어팩 의존. MCP private-LAN HTTP는 신뢰 서버·사용자 승인 필요.
5. 네트워크/SDK, 운영 AdMob 동의·승인 및 Play 정책/서명 검증. 소스의 앱 전용 저장이 독립 암호화나 실제 네트워크 보안 보장을 뜻하지 않는다. P0/P1 결함 0건 확인 불가.

## 10-F 추가 도구

`scripts/collect_ci_evidence.py` — 정확한 개발 브랜치, 앱 커밋, 실행 ID, workflow path, run attempt, job/step 성공 및 artifact metadata 확인. 실제 파일·로그와 실기기 증빙은 별도 검토한다. 빈 16KB 게이트를 metadata만으로 PASS 처리하지 않는다. 기존 evidence.json 덮어쓰기 금지. 수집기 자체의 신규 CI 및 실제 gh CLI 수집 실행은 별도 검증 대상.

예시 (GitHub CLI 인증된 개발 PC, 새 폴더):
```bash
python scripts/collect_ci_evidence.py --commit c560d4b02db57682d6828bfda4751ece21ca5659 --java-run 45784969406 --native-run 45784969284 --out onhand-10e-evidence
```

수집이 성공해도 최종 게이트는 BLOCKED가 정상이다. CI 조회와 SHA-256 해시 파일 생성은 실제 검수 실행이나 증빙의 진실성 확인이 아니다.

최초 실행 광고 및 개발용 광고 잠금 보존. main 병합·운영 광고·스토어 제출 없음.
