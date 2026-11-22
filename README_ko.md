# Ollama Pocket — 안드로이드 기기 자체에서 Ollama + LLM 실행

**PC 서버가 필요하지 않은 독립형 안드로이드 앱 프로젝트**입니다. 앱이 자체 실행한 Ollama 서버(127.0.0.1:11434)를 통해 스마트폰 CPU에서 모델을 구동합니다. Termux도 필요하지 않습니다.

## 기능

- Ollama 서버 자동 시작 / 수동 시작·종료 (포그라운드 서비스)
- 다운로드: `gemma3:1b`, `qwen2.5:0.5b` 등 Ollama 모델 이름 입력
- 설치된 모델 목록 / 선택 / 삭제
- GGUF 파일 가져오기 → Ollama 모델로 로컬 등록
- API 스트리밍 채팅, 응답 중지, 모델별 대화 기록 유지
- 서버 로그 및 버전 확인
- 오프라인 추론 (첫 모델 다운로드에만 인터넷 필요)
- 네트워크 바인딩은 **127.0.0.1:11434** 한정, 외부 네트워크 공개 안 함
- Android 8.0 이상, 64비트 ARM CPU만 지원, **CPU 추론**

## 실행 가능한 APK 만드는 방법 (권장: GitHub Actions)

이 ZIP은 **Android Studio 소스 프로젝트**입니다. 실제 Ollama ARM64 실행파일과 완성 APK는 포함하지 않습니다.

1. 이 프로젝트 전체를 개인 GitHub 저장소에 업로드합니다. `.github/workflows/build-apk.yml`까지 포함되어야 합니다.
2. 저장소의 **Actions** 탭에서 `Build Ollama Pocket Android APK` → **Run workflow**를 누릅니다.
3. 실행에 성공하면 결과 화면 **Artifacts**에서 `OllamaPocket-Android-APK`를 받습니다.
4. 압축 안의 `app-debug.apk`를 스마트폰에 설치합니다.
5. 앱에서 **모델 → 모델 다운로드**로 `qwen2.5:0.5b`를 먼저 설치하고, **대화** 탭에서 실행합니다.

> Actions가 Rllama (Ollama의 Android 지원 커뮤니티 포크) 소스를 컴파일하여 실제 `libollama.so`와 `libllama-server.so`를 APK에 탑재합니다. 네트워크가 필요할 때는 모델 다운로드 요청만 외부로 나갑니다.

## Android Studio에서 직접 빌드

1. JDK 17, Android SDK API 35, NDK 27+, CMake 3.24+, Go(포크 소스의 `go.mod` 버전)를 준비합니다.
2. 저장소 루트에서 `ANDROID_NDK_HOME`을 설정하고 `bash scripts/build_native.sh`를 실행합니다.
3. `app/src/main/jniLibs/arm64-v8a/`에 `libollama.so`, `libllama-server.so`가 있는지 확인합니다.
4. Android Studio에서 본 프로젝트를 열어 Gradle Sync 후 **Build APK(s)** 실행 또는 `gradle :app:assembleDebug`.
5. APK 위치: `app/build/outputs/apk/debug/app-debug.apk`.

일반적인 PC 없이 GitHub Actions에서 APK를 빌드할 수 있지만 GitHub 계정과 저장소 업로드는 필요합니다.

## 스마트폰 추천 모델

| RAM | 우선 테스트할 모델 | 참고 |
|---|---|---|
| 4~6 GB | `qwen2.5:0.5b` | 가벼운 테스트용 |
| 8 GB | `gemma3:1b`, `qwen2.5:1.5b` | 기본 추천 |
| 12 GB 이상 | `qwen2.5:3b` 등 | 발열·속도·여유 RAM에 따라 다름 |

데스크톱의 7B·14B 모델은 일반적인 폰에서 느리거나 메모리 부족으로 종료될 수 있습니다. 모델 파일 용량 외에도 KV 캐시 및 Android용 RAM이 필요합니다.

## GGUF 파일 직접 가져오기

1. 모델 관리 탭에서 **GGUF 파일 가져오기** 선택
2. 스마트폰 파일 선택기에서 `.gguf` 파일 선택
3. 앱 내부 저장소로 임시 복사 후 `ollama create imported-<timestamp> -f <Modelfile>` 실행
4. 등록이 성공하면 임시 GGUF 복사본을 삭제. 등록 실패해도 임시 복사본은 정리합니다. 모델이 매우 크면 여유 저장 공간이 필수입니다.

## 기술 정보 및 검증 한계

- UI: Android Java + 기본 Android View (추가 UI 라이브러리 없음)
- 서버: **실제 Ollama** Android ARM64 네이티브 바이너리 (커뮤니티 포크 소스에서 컴파일)
- 추론 실행: Ollama가 기기 내부의 `llama.cpp` runner 호출
- 포그라운드 서비스와 지속 알림 사용. Android 제조사별 백그라운드/발열 정책 때문에 프로세스가 종료될 수 있음
- 일부 최신 Ollama 모델/멀티모달 기능은 ARM64 CPU-only 포크와 호환되지 않을 수 있음
- **현재 전달물 검증:** 소스 및 구조 점검. 이 작업 환경에는 Android SDK/NDK와 스마트폰 실기가 없어 **APK 빌드, 기기 설치, 실제 토큰 생성은 검증하지 못함**.
- 안드로이드 최신 버전의 제한으로 백그라운드 실행이 중단될 수 있으며, 가능하면 앱을 화면에 둔 상태에서 시작하세요.

## 라이선스 및 신뢰

- 본 프로젝트 자체 UI/Android 코드: MIT-0 (자유 사용)
- Ollama 런타임: `RadekParek/Rllama` (Ollama/llama.cpp 및 의존 라이선스 준수)
- 출처: https://github.com/RadekParek/Rllama 및 https://github.com/ollama/ollama
- 빌드 파이프라인은 외부 실행 파일을 무작정 내려받지 않고 공개된 포크 소스에서 바이너리를 컴파일합니다. 실행 전 포크 코드를 직접 검토하는 것을 권장합니다.

## 관련 API

- GET `/api/version`, `/api/tags`
- POST `/api/pull`, `/api/chat`
- DELETE `/api/delete`
- 로컬 네트워크만: `http://127.0.0.1:11434`
