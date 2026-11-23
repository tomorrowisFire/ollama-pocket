# AI in Pocket - Client for Ollama (v0.8.0 / Cooperative Safe RAM)

안드로이드 기기 자체에서 **Ollama Android ARM64 엔진**을 실행하는 비공식 독립 클라이언트입니다. PC·Termux·원격 서버 없이 기기에서 모델 다운로드와 로컬 텍스트 대화를 수행하도록 개발했습니다. 실행용 바이너리는 공개 커뮤니티 포크(Rllama) 소스에서 빌드합니다. 이미지/영상 탭은 요청에 따라 제거했습니다.

## 앱 기능

- 간단한 **채팅 / 모델 / 설정** 3개 탭
- 첫 실행의 닫을 수 있는 사용법 안내 화면과 Google 공식 테스트 배너
- 로컬 RAM·현재 여유 RAM·CPU 코어·ABI·여유 저장공간 진단, 맞춤 모델 추천(추정치, 성능 보장 아님)
- Ollama API로 모델 다운로드/목록/삭제, 모델별 대화 저장 및 응답 스트리밍/중단
- 다운로드 중 진행률 표시(모델의 개별 전송 레이어 기준), 진행률 아래 광고 슬롯
- 다운로드 확인창과 첫 실행 안내에 **인터넷 데이터 사용 및 통신사 요금 발생 가능성** 표시
- GGUF 가져오기, 로컬 서버 자동 시작 및 수동 중지
- Google UMP 동의 확인 후 Google AdMob 공식 테스트 광고 요청; 개인정보 설정 링크
- 모델 다운로드 및 광고 표시에는 인터넷이 필요하며 로컬 대화 텍스트는 외부 AI 클라우드로 전송하지 않음

## APK 설치

1. GitHub 저장소 **Actions → Build AI in Pocket Android APK**에 접속합니다.
2. 최신 성공 기록을 연 뒤 아래의 **Artifacts → AI-in-Pocket-Android-APK**를 내려받습니다.
3. ZIP 내부의 **AI-in-Pocket-v0.4.0-android-arm64.apk**를 설치합니다.
4. 처음 실행하면 안내를 확인하고 **모델 선택 시작**을 선택합니다.
5. 기기 추천 모델을 선택합니다. 데이터 요금 안내 확인 뒤 모델을 다운로드합니다.
6. 채팅 탭에서 설치 모델을 사용합니다. 다운로드 뒤 로컬 대화에는 인터넷 연결이 필요하지 않습니다.

### 빌드

GitHub Actions가 Android SDK 35, NDK, Go, Rllama 소스를 이용해 `libollama.so`, `libllama-server.so`를 컴파일한 뒤 Gradle로 Debug APK를 조립합니다. Gradle 의존성: Google Mobile Ads 25.5.0 및 UMP 4.0.0. 빌드 산출물은 **테스트 및 개인 설치용**으로, Play 스토어 출시용 서명·정책 제출은 별도입니다.

## 모델 추천과 데이터 사용

기기 정보는 시스템 API에서 가져오며 추천을 위한 정보 자체는 서버로 보내지 않습니다. 추천 모델의 용량·속도·메모리 필요량은 예상치이므로 기기 상태에 따라 실패하거나 느릴 수 있습니다. 모델 파일(수백 MB~수 GB) 다운로드 시 인터넷 데이터가 사용됩니다. 광고를 표시하는 경우에도 Google 네트워크 데이터가 사용되며 모바일 통신료가 발생할 수 있습니다. Wi-Fi 이용을 권장합니다. 다운로드 시간은 네트워크·모델에 따라 달라집니다.

## 라이선스·상표·광고 주의사항

- 사용자 지정 앱 명칭: **AI in Pocket - Client for Ollama**
- **Ollama 공식 앱이 아니며 Ollama 측과 제휴하지 않습니다.** ‘Client for Ollama’는 호환성 설명 목적입니다. 상표 적법성의 최종 판단이나 공개 배포 승인까지 보장하지 않습니다.
- 앱 자체 Android 소스 MIT-0. Ollama, llama.cpp 및 기타 종속 코드와 모델 가중치의 라이선스는 별도로 적용됩니다. `NOTICE.txt`, `LICENSE`, APK 내부 `assets/licenses`를 참고하세요.
- 현재 광고는 **Google 공식 테스트 광고 ID**만 사용합니다. 광고 수익이 발생하지 않습니다. 출시 전 본인 AdMob 앱/광고 단위 ID, UMP 개인정보 메시지, 개인정보처리방침, 광고 관련 Play Console 정책 설정 및 실제 기기 검증이 필요합니다.
- Google 모바일 광고 SDK가 데이터를 제3자에게 전송할 수 있으므로 앱의 기본 로컬 LLM 추론과 광고 네트워크를 구분해야 합니다.
- 실기에서 서버 시작, 모델 설치, CPU 추론 및 개인정보 동의 흐름을 검증하기 전에는 안정적으로 작동한다고 보장할 수 없습니다.

## 지원 범위

- Android 8.0(API 26)+, ARM64. CPU 추론 및 localhost 127.0.0.1:11434 바인딩.
- 모델에 따라 Android 커뮤니티 포크와의 호환성이 달라질 수 있습니다.
- 이미지·영상 생성은 현재 지원하지 않으며 UI 탭도 포함하지 않습니다.


## AdMob 운영 광고 전환 준비

광고 단위 ID는 발급된 다음 세 가지를 release용 코드에 입력했습니다.
- 앱 오프닝: `ca-app-pub-4763897710237099/8245753612` (최초 사용 안내를 방해하지 않도록 앱 복귀 시 제한적 표시)
- 모델 다운로드 중 배너: `ca-app-pub-4763897710237099/7124243639`
- 최초 실행 닫기 가능한 안내 배너: `ca-app-pub-4763897710237099/9558835282`

**중요:** AdMob 앱 ID는 광고 단위 ID와 다릅니다. AdMob 앱 설정의 `ca-app-pub-...~...` 형식 앱 ID가 추가로 필요합니다. 개발용 `assembleDebug`는 Google 테스트 앱/광고 단위 ID를 사용해 무효 트래픽을 방지합니다. 실제 광고가 연동된 release APK를 빌드하려면 `ADMOB_APP_ID=ca-app-pub-...~...` 환경변수나 Gradle 속성을 설정해야 합니다. 앱 ID가 없으면 release는 빌드 오류를 발생시켜 잘못된 배포를 방지합니다. 수익 광고 전에는 AdMob 앱 연결 및 계정 검토, 개인정보·동의(UMP), 테스트 기기 설정을 확인하세요.


## CPU / GPU / NPU 연산 구분 (v0.3 기록 — v0.4 업데이트는 문서 하단 참고)

- 기기 진단은 CPU 코어 수, Android Vulkan Compute 지원 여부, NNAPI 드라이버 유형(CPU/GPU/ACCELERATOR)을 각각 표시합니다.
- 이 진단은 개인 식별정보 없이 기기에서만 수행되며, NNAPI의 ACCELERATOR는 **NPU일 가능성이 있는 가속기 후보**일 뿐 NPU 성능을 보증하지 않습니다. Android 15 이후 NNAPI는 deprecated이며 제조사별 노출 정책이 다릅니다.
- 설치되는 Ollama Android 빌드는 **CPU 추론 전용**입니다. `num_gpu=0`, `OLLAMA_VULKAN=0`이며 GPU/NPU 추론으로 오인하지 않도록 UI에 명확히 표기합니다.
- GPU·NPU 백엔드가 탑재되지 않은 상태에서 두 모드를 선택하면 미지원 사유만 안내합니다. CPU/자동(현재 CPU) 모드는 실제로 선택할 수 있습니다.
- GPU에서 실제 추론하려면 Vulkan/OpenCL 네이티브 backend와 Ollama runner 통합 검증이 필요합니다. NPU는 Snapdragon Hexagon 등의 별도 backend·드라이버·양자화 호환성 검증이 선행돼야 합니다. **GPU/NPU 추론 가속은 아직 구현되지 않았습니다.**
- GitHub Actions가 NNAPI 장치 드라이버 탐지용 `libdeviceprobe.so`를 Android NDK로 컴파일해 APK에 포함합니다.

## v0.4.0 — Open WebUI의 사용성에서 참고한 독립 모바일 UI

- 실제 앱은 Open WebUI 코드·로고·에셋을 가져오지 않습니다. 모델 선택기를 채팅 상단에, 신규 채팅과 대화 기록을 상단 메뉴에 배치하고, 간결한 채팅·모델·설정 하단 탭을 지원합니다.
- 모델별 대화가 여러 세션으로 저장됩니다. 기존 대화는 기기에 저장되며 인터넷 없이 열 수 있습니다.
- 기존 채팅·모델 다운로드·실시간 응답·GGUF 가져오기·광고·데이터 요금 고지 기능을 유지합니다.

## 실제 CPU / GPU / NPU 연산과 우선순위

1. **CPU:** 검증된 Android ARM64 Ollama 서버(127.0.0.1:11434)로 GGUF 모델을 실행합니다. 별도 계정·PC가 필요하지 않습니다.
2. **GPU:** GitHub Actions가 `llama.cpp` Vulkan runner(별도 서버, 127.0.0.1:11436)를 옵션으로 빌드합니다. 스마트폰 Vulkan Compute 기능, APK 안의 `libllama-vulkan-server.so` 파일, 다운로드된 Ollama 모델의 GGUF 가중치, 실제 GPU 레이어 오프로드 로그, `/health` 응답을 확인한 경우에만 GPU 사용으로 표시합니다. 하나라도 실패하면 CPU Ollama로 자동 전환합니다. Vulkan runner는 Ollama 자체의 Android GPU 기능이 아니라 별도의 호환 inference engine입니다.
3. **NPU:** NNAPI 후보 드라이버를 진단하지만 **GGUF 추론에 사용할 수 있는 제조사 별 가속 백엔드는 탑재하지 않았습니다.** 따라서 NPU를 선택·실행했다고 표시하지 않습니다. NPU에 대한 실연산은 추후 기종별 SDK/모델 변환·성능 검증 작업이 선행되어야 합니다.
4. **자동:** GPU runner가 포함되고 검증 가능한 상황이면 GPU를 시도하며, 그렇지 않으면 CPU를 사용합니다. **CPU 고정** 또는 **GPU 우선** 모드도 설정에서 선택할 수 있습니다. GPU 우선에서 실행 실패해도 CPU로 돌아갑니다.

GPU runner 소스는 MIT 라이선스 `ggml-org/llama.cpp` 태그 `b11913`을 사용하며, Android NDK r27c와 Vulkan 헤더·`glslc`를 이용해 빌드합니다. 일부 GPU 드라이버에서 Vulkan llama.cpp 실행 중 충돌이 발생할 수 있으며, APK에 GPU runner가 존재한다고 GPU 작동을 보증할 수 없습니다. GitHub Actions에서 GPU 빌드가 실패하더라도 CPU Ollama APK는 정상적으로 제작합니다. 빌드 실행 페이지의 `android-vulkan-build-log`를 참고하세요.

### 검증 상태

- GitHub Actions가 **APK 빌드 성공**을 표시할 때에만 빌드 완료입니다.
- 실제 기기에서 모델 다운로드, CPU/GPU 토큰 생성과 NNAPI 후보 탐지는 별도로 실기 테스트가 필요합니다.
- GPU 실패 원인은 앱 실행 로그 및 GPU logs를 통해 점검할 수 있습니다.


## v0.7 실험적 협력 추론 (안드로이드 동일 Wi-Fi)
- 메인 호스트에서 **설정 → 협력 추론 → 호스트 세션 만들기**, 보조 기기에서 **동일 Wi-Fi 세션 검색** 후 연결 코드를 입력하고 참가합니다.
- NSD/mDNS 검색이 차단되면 호스트에 표시된 IP·세션 ID·256비트 연결 코드를 수동 입력할 수 있습니다.
- 보조 기기마다 CPU `ggml-rpc-server`를 `127.0.0.1:50052`에서 실행하며, 호스트는 `llama-server --rpc`에 루프백 중계 주소를 연결합니다. **Ollama API 자체를 RPC 엔진으로 사용하지 않습니다.**
- 기기 사이 RPC 트래픽은 애플리케이션의 인증 및 AES-256-GCM 암호화 중계를 통과합니다. 세션 키는 mDNS로 공개하지 않으며 신뢰하는 기기끼리 안전한 방법으로 공유해야 합니다.
- 양쪽에 **동일 APK**를 설치합니다. 참가할 때 RPC 워커 실행 파일 SHA-256을 비교합니다. 세션은 최대 3개 보조 기기·45분이며 종료 버튼이 있습니다.
- GGUF는 호스트에 다운로드하며, 보조 기기는 `-c` 로컬 RPC 캐시를 사용합니다. 초기에는 기기별 메모리 자동 분배에 맡기며 안전 RAM 진단은 **상한 강제 기능이 아닙니다.**
- Android 8+/ARM64 대상. 보조 기기 CPU RPC를 우선 구현한 **실험 빌드**입니다. Vulkan GPU RPC·NPU RPC는 검증 전이며, 실물 2대에서 분산된 토큰 생성·속도·발열·장애 복구를 아직 검증하지 않았습니다.
- RPC 원본은 실험적이며 안전하지 않습니다. 앱은 암호화 중계를 사용하지만 실기 보안 검증 전에는 공개 Wi-Fi/인터넷에서 사용하지 마세요.
- 빌드: GitHub Actions → `Experimental cooperative RPC Android APK` → `AI-in-Pocket-Cooperative-RPC-APK` 다운로드.

## v0.8 협력 추론 RAM 안전 예산 (2026-11-23)

- 호스트와 보조 기기의 실제 `ActivityManager.MemoryInfo` 값(`totalMem`, `availMem`, `threshold`, `lowMemory`)으로 **현재 추정 안전 할당량**을 계산합니다. 고정 물리 RAM 합산이 아닙니다.
- 보조 노드는 연결 시 및 10초 주기 인증된 헬스체크에서 추정 안전 RAM을 갱신하며, RAM 필드 자체가 메시지 HMAC에 포함됩니다. 기존 세션과 호환되지 않으므로 **모든 기기를 v0.8 APK로 통일**합니다.
- 호스트에서는 보조 기기를 ID 순으로 정렬해 RPC 포트 및 `--tensor-split` 비율을 동일 순서로 만듭니다. 안전 RAM 384 MiB 미만 노드는 새 모델 로딩 대상에서 제외됩니다.
- 모델 파일의 실제 GGUF 바이트 수를 기준으로 `가중치 + max(768 MiB, 가중치의 20%)`를 최소 필요량으로 추정하고 안전 메모리보다 크면 **시작하지 않습니다**.
- 분산 엔진에 `--gpu-layers auto --fit on --fit-target 512 --tensor-split <remote-budgets>`를 전달합니다. 호스트 CPU는 tensor-split 목록에 넣지 않습니다. 이는 **보수적인 계획·휴리스틱**으로 Android 프로세스의 하드 메모리 상한을 강제하지 않습니다.
- 협력 세션 화면의 **선택 모델의 RAM 안전 배정량 점검**을 누르면 로딩 전에 안전 합계·필요량·보조 기기별 분할 비율을 확인할 수 있습니다.
- 16GB 호스트 + 4GB 보조 2대에서 14B Q4 실행은 조건부입니다. 기기별 실시간 여유 RAM이 부족하면 차단합니다. 한 번 로딩된 모델은 실행 중 메모리 변동만을 이유로 재시작하지 않습니다.
- 자동 검사: pure Java RAM 예산·분할 단위 테스트와 암호화 중계 테스트, Android Java 컴파일, ARM64 바이너리 및 APK 검사.
- **실제 2~3대 Android 기기 RPC 토큰 생성·성능·발열 검증은 별도 필요**하며 빌드 성공만으로 실측 검증 완료라고 보고하지 않습니다.
